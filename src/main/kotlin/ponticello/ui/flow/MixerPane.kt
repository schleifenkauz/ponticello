package ponticello.ui.flow

import fxutils.*
import fxutils.actions.*
import fxutils.controls.PropertySelectorButton
import fxutils.drag.ConfiguredDropHandler
import fxutils.drag.setupDropArea
import fxutils.prompt.SelectorPrompt
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.input.*
import javafx.scene.layout.*
import javafx.scene.shape.Polygon
import javafx.scene.text.Text
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import org.kordamp.ikonli.materialdesign2.MaterialDesignV
import ponticello.impl.*
import ponticello.model.flow.AudioFlow
import ponticello.model.flow.AudioFlows
import ponticello.model.flow.MixerFlow
import ponticello.model.flow.MixerFlow.Companion.VOLUME_SPEC
import ponticello.model.instr.BusObject
import ponticello.model.obj.BusReference
import ponticello.model.obj.project
import ponticello.model.project.PonticelloProject
import ponticello.model.project.flows
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.model.server.BusRegistry
import ponticello.ui.actions.ServerActions
import ponticello.ui.actions.undoable
import ponticello.ui.controls.DecimalPrompt
import ponticello.ui.controls.Knob
import ponticello.ui.dock.MixerPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.getFrom
import ponticello.ui.midi.AkaiMidiMix
import ponticello.ui.misc.MidiDeviceSelectorPrompt
import ponticello.ui.registry.ListDisplayConfig
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView
import reaktive.Reactive
import reaktive.value.*
import reaktive.value.binding.equalTo
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiSystem

class MixerPane(
    private val listConfig: MixerComponentListConfig,
) : ToolPane(), ListDisplayConfig<MixerFlow.MixerComponent> by listConfig {
    override val type: Type
        get() = MixerPane

    override val boxStyle: Array<String>
        get() = arrayOf("object-box", "fader-box")

    private var channelsList: ObjectListView<MixerFlow.MixerComponent>? = null
    private var masterFaderBox: Control? = null

    override val content: Parent
        get() {
            val mixer = selectedMixer.get()
            return if (mixer != null) {
                val masterFader = createFaderBox(
                    mixer.targetBus, mixer.masterVolume, obj = null,
                )
                masterFaderBox = (object : Control() {}).styleClass(*boxStyle).apply {
                    setRoot(masterFader)
                    setPseudoClassState("inline-content", true)
                    setOnMouseClicked { requestFocus() }
                }
                HBox(10.0, channelsList, masterFaderBox).alwaysVGrow()
            } else BorderPane(Label("No mixer selected"))
        }

    private val midiMixer = AkaiMidiMix()

    private var selectedMixer: ObjectReference<MixerFlow> = ObjectReference.none()
        set(value) {
            if (field == value) return
            field = value
            val flow = value.get()
            listConfig.setMixer(flow)
            channelsList = flow?.let { m -> ObjectListView(m.components, this, scrollable = true) }
            midiMixer.flow = flow
            relayout()
            runAfterLayout {
                window?.sizeToScene()
            }
        }

    private var selectedDevice: MidiDeviceSelectorPrompt.Option = MidiDeviceSelectorPrompt.Option.NoDevice
        set(value) {
            if (value is MidiDeviceSelectorPrompt.Option.Device) {
                try {
                    midiMixer.detach()
                    midiMixer.attachTo(value.info)
                } catch (e: Exception) {
                    Logger.error("Unable to attach to $value")
                    field = MidiDeviceSelectorPrompt.Option.NoDevice
                    return
                }
            }
            field = value
        }

    private val deviceSelector = PropertySelectorButton(
        this::selectedDevice,
        prompt = MidiDeviceSelectorPrompt(),
        defaultValue = MidiDeviceSelectorPrompt.Option.NoDevice
    )

    private val mixerSelector = PropertySelectorButton(
        this::selectedMixer,
        prompt = MixerListPopup(),
        defaultValue = ObjectReference.none()
    )

    fun selectMixer(mixer: MixerFlow) {
        mixerSelector.update(mixer.reference())
    }

    fun selectDevice(info: MidiDevice.Info) {
        deviceSelector.update(MidiDeviceSelectorPrompt.Option.Device(info))
    }

    override val headerContent: Node
        get() {
            return if (channelsList == null) mixerSelector
            else {
                HBox(
                    THUMB_SIZE, mixerSelector,
                    Label("MIDI:"), deviceSelector,
                    toggleFiltersAction.withContext(selectedMixer.force()).makeButton("medium-icon-button"),
                    ActionBar(channelsList!!.actions, "medium-icon-button")
                ).centerChildren()
            }
        }

    override val supportedModes: Collection<ObjectListView.DisplayMode>
        get() = setOf(ObjectListView.DisplayMode.Inline(collapsable = false))

    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override fun defaultState(): ToolPaneState = MixerPaneState.default()

    override fun afterSetup() {
        val state = initialState
        if (state is MixerPaneState) {
            state.flowReference.resolve(allMixerFlows())
            mixerSelector.update(state.flowReference)
            if (state.midiDeviceName != null) {
                val device = MidiSystem.getMidiDeviceInfo().find { d ->
                    d.name == state.midiDeviceName && d.javaClass.simpleName.startsWith("MidiIn")
                }
                if (device != null) {
                    deviceSelector.update(MidiDeviceSelectorPrompt.Option.Device(device))
                } else {
                    Logger.warn("Unable to find midi device ${state.midiDeviceName}", Logger.Category.AudioFlow)
                }
            }
        }
        setupVolumeChangeWithArrowKeys()
        registerShortcuts {
            if (channelsList != null) {
                registerActions(channelsList!!.actions)
            }
        }
        setupDropArea(MixerPaneDropHandler())
    }

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> = when {
        dragboard.hasContent(BusObject.DATA_FORMAT) -> arrayOf(TransferMode.LINK)
        else -> emptyArray()
    }

    override fun getDroppedObjects(
        ev: DragEvent,
        targetView: ObjectListView<MixerFlow.MixerComponent>,
    ): List<MixerFlow.MixerComponent> {
        val bus = ev.dragboard.getFrom(context[BusRegistry], BusObject.DATA_FORMAT) ?: return emptyList()
        return listOf(MixerFlow.MixerComponent.create(bus))
    }

    private fun setupVolumeChangeWithArrowKeys() {
        addEventFilter(KeyEvent.KEY_PRESSED) { ev ->
            if (ev.modifiers.isNotEmpty()) return@addEventFilter
            val selectedVolume =
                if (ev.target == masterFaderBox) selectedMixer.get()?.masterVolume ?: return@addEventFilter
                else channelsList?.selectedObject()?.volume ?: return@addEventFilter
            val delta = when (ev.code) {
                KeyCode.DOWN -> -VOLUME_SPEC.step.get()
                KeyCode.UP -> VOLUME_SPEC.step.get()
                else -> return@addEventFilter
            }
            setVolume(selectedVolume, selectedVolume.now + delta)
        }
    }

    private fun setVolume(
        variable: ReactiveVariable<Decimal>, value: Decimal,
        updateDescription: String = "Change Volume",
    ) {
        VariableEdit.updateVariable(
            variable, value,
            context[UndoManager], updateDescription
        )
    }

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is MixerPaneState) {
            dest.flowReference = selectedMixer
            val device = selectedDevice as? MidiDeviceSelectorPrompt.Option.Device
            dest.midiDeviceName = device?.info?.name
        }
    }

    override fun expandedLayout(box: ObjectBox<MixerFlow.MixerComponent>): Node {
        val bus = box.obj
        return createFaderBox(bus.sourceBus, bus.volume, bus)
    }

    override fun contentUpdate(obj: MixerFlow.MixerComponent): Reactive = obj.sourceBus

    private fun createFaderBox(
        bus: ReactiveValue<BusReference>, volume: ReactiveVariable<Decimal>, obj: MixerFlow.MixerComponent?,
    ): VBox {
        val namePane = createVerticalNameLabel(bus)
        val volumeBox = createVolumeBox(volume, bus)
        val scopeButton = ServerActions.scopeBus.withContext(bus).makeButton("medium-icon-button")

        val audioBus = bus.now.get()!! as BusObject.AudioBus
        val meter = LevelMeter(audioBus, offset = obj?.volume ?: reactiveValue(zero), meterWidth = 8.0)
        val thumb = createFaderThumb(meter, volume)
        val faderContainer = Pane(meter, thumb)
        meter.heightProperty().bind(faderContainer.heightProperty())
        val (top, bottom) =
            if (obj != null) createPanKnobAndMuteSoloActionsBar(obj)
            else createMasterMonoAndMuteButtons()
        val layout = VBox(
            HBox(infiniteSpace(), top, infiniteSpace()),
            Region() styleClass "fader-separator",
            scopeButton.centered(),
            volumeBox,
            HBox(hspace(3.0), namePane, infiniteSpace(), faderContainer).alwaysVGrow(),
            Region() styleClass "fader-separator",
            bottom
        ) styleClass "fader-layout"
        return layout
    }

    private fun createFaderThumb(meter: LevelMeter, volume: ReactiveVariable<Decimal>): Polygon {
        val triangle = Polygon(0.0, -THUMB_SIZE / 2, 0.0, THUMB_SIZE / 2, THUMB_SIZE, 0.0).styleClass("fader-thumb")
        triangle.userData = volume.forEach { v ->
            Platform.runLater {
                triangle.layoutY = meter.levelToY(v.toDouble())
            }
        }
        meter.heightProperty().addListener {
            triangle.layoutY = meter.levelToY(volume.now.toDouble())
        }
        triangle.layoutXProperty().bind(meter.widthProperty().subtract(LevelMeter.SCALE_WIDTH))
        triangle.setOnMouseClicked { ev ->
            triangle.requestFocus()
            ev.consume()
        }
        var startY = Double.NaN
        var startVolume = volume.now
        triangle.setOnMousePressed { ev ->
            startVolume = volume.now
            startY = ev.screenY
            ev.consume()
        }
        triangle.setOnMouseDragged { ev ->
            if (startY.isNaN()) return@setOnMouseDragged
            val deltaY = ev.screenY - startY
            val deltaVolume = deltaY * -(LevelMeter.LEVEL_RANGE / meter.height)
            val newVolume = (startVolume + deltaVolume)
                .coerceIn(LevelMeter.MIN_LEVEL.toDecimal(), LevelMeter.MAX_LEVEL.toDecimal())
            setVolume(volume, newVolume)
            ev.consume()
        }
        return triangle
    }

    private fun createMasterMonoAndMuteButtons(): Pair<HBox, StackPane> {
        val monoButton = monoAction.withContext(selectedMixer.get()!!)
            .makeTextButton("selector-button").styleClass("mono-button")
        val monoButtonPane = HBox(infiniteSpace(), monoButton, infiniteSpace()).centerChildren()
        monoButtonPane.prefHeight = 41.0

        val masterMuteButton =
            masterMuteAction.withContext(selectedMixer.get()!!).makeButton("medium-icon-button")

        return Pair(monoButtonPane, masterMuteButton.centered())
    }

    private fun createPanKnobAndMuteSoloActionsBar(obj: MixerFlow.MixerComponent): Pair<Knob, Node> {
        val panKnob = listConfig.createPanKnob(obj.pan, radius = 20.0)

        val muteAndSolo = ActionBar(
            MixerComponentListConfig.muteAndSolo.withContext(obj),
            buttonStyle = "mute-solo-button"
        )

        return Pair(panKnob, HBox(infiniteSpace(), muteAndSolo, infiniteSpace()))
    }

    private fun createVolumeBox(
        volume: ReactiveVariable<Decimal>,
        bus: ReactiveValue<BusReference>,
    ): HBox {
        val valueLabel = label(volume.map { v -> "$v db" }) styleClass "fader-volume"
        val volumeBox = HBox(infiniteSpace(), valueLabel, infiniteSpace()) styleClass "fader-volume-box"
        volumeBox.setOnMouseClicked { ev ->
            val title = "Volume of ${bus.get().name.now}"
            volume.now = DecimalPrompt(title, volume.now, VOLUME_SPEC.range)
                .showDialog(ev) ?: return@setOnMouseClicked
        }
        setMargin(volumeBox, Insets(0.0, 3.0, 0.0, 3.0))
        return volumeBox
    }

    private fun createVerticalNameLabel(bus: ReactiveValue<BusReference>): StackPane {
        val nameLabel = Text()
        nameLabel.textProperty().bind(bus.flatMap(BusReference::name).asObservableValue())
        val namePane = makeVerticalLabel(nameLabel).setFixedWidth(15.0)
        return namePane
    }

    override fun getContent(obj: MixerFlow.MixerComponent, box: ObjectBox<MixerFlow.MixerComponent>): Parent = Region()

    override fun getDragTarget(box: ObjectBox<MixerFlow.MixerComponent>): Node = box

    private fun allMixerFlows() = context.project.flows.allFlows().filterIsInstance<MixerFlow>()

    private inner class MixerListPopup : SelectorPrompt<ObjectReference<MixerFlow>>("Select mixer") {
        override fun options(): List<ObjectReference<MixerFlow>> =
            allMixerFlows().map { f -> f.reference() } + ObjectReference.none()

        override fun createCell(option: ObjectReference<MixerFlow>): Region =
            HBox(label(option.name) styleClass "option-label")

        override fun extractText(option: ObjectReference<MixerFlow>): String = option.name.now
    }

    private inner class MixerPaneDropHandler : ConfiguredDropHandler({
        handleTypedFormat(AudioFlow.DATA_FORMAT, TransferMode.LINK) { _, ref ->
            val flow = ref.resolve(context[AudioFlows].allFlows()) ?: return@handleTypedFormat false
            if (flow !is MixerFlow) {
                Logger.warn("Dropped flow is not a MixerFlow", Logger.Category.AudioFlow)
                return@handleTypedFormat false
            }
            mixerSelector.update(flow.reference())
            true
        }
    })

    companion object : Type(15, "Mixer") {
        override val defaultSide: Side
            get() = Side.BOTTOM
        override val icon: Ikon
            get() = MaterialDesignT.TUNE_VERTICAL

        override val shortcut: String
            get() = "F10"

        private const val THUMB_SIZE = 10.0

        override fun createToolPane(project: PonticelloProject): ToolPane =
            MixerPane(MixerComponentListConfig())

        private val monoAction = action<MixerFlow>("Mono") {
            enableWhen { mixer -> mixer.targetChannels.equalTo(2) }
            toggles(MixerFlow::monoMix)
            undoable()
        }

        private val masterMuteAction = action("Mute master") {
            toggles(
                MixerFlow::masterMute,
                whenTrue = MaterialDesignV.VOLUME_MUTE,
                whenFalse = MaterialDesignV.VOLUME_HIGH
            )
            undoable()
        }

        private val toggleFiltersAction = action("Toggle filters") {
            icon(MaterialDesignC.CAR_CRUISE_CONTROL)
            toggles(MixerFlow::activateFilters)
            undoable()
        }
    }
}