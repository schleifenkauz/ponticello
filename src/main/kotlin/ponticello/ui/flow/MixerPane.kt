package ponticello.ui.flow

import fxutils.*
import fxutils.actions.*
import fxutils.prompt.SearchableListView
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.input.DataFormat
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Text
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import org.kordamp.ikonli.materialdesign2.MaterialDesignV
import ponticello.impl.Decimal
import ponticello.impl.unaryMinus
import ponticello.impl.withPrecision
import ponticello.model.flow.MixerFlow
import ponticello.model.flow.MixerFlow.Companion.VOLUME_SPEC
import ponticello.model.obj.BusObject
import ponticello.model.obj.BusReference
import ponticello.model.obj.project
import ponticello.model.project.PonticelloProject
import ponticello.model.project.flows
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.ui.actions.ServerActions
import ponticello.ui.actions.undoable
import ponticello.ui.controls.DecimalPrompt
import ponticello.ui.controls.Knob
import ponticello.ui.dock.MixerPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.registry.ListDisplayConfig
import ponticello.ui.registry.ObjectListView
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.equalTo
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now

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

    private var selectedMixer: ObjectReference<MixerFlow> = ObjectReference.none()
        set(value) {
            if (field == value) return
            field = value
            val mixer = value.get()
            listConfig.setMixer(mixer)
            channelsList = mixer?.let { m -> ObjectListView(m.components, this, scrollable = true) }
            relayout()
            runAfterLayout {
                window?.sizeToScene()
            }
        }

    override val headerContent: Node
        get() {
            val selector = MixerListPopup().selectorButton(this::selectedMixer, actionDescription = "Select mixer")
            return if (channelsList == null) selector
            else HBox(selector, ActionBar(channelsList!!.actions, "medium-icon-button"))
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
            selectedMixer = state.flowReference
        }
        setupVolumeChangeWithArrowKeys()
        registerShortcuts {
            if (channelsList != null) {
                registerActions(channelsList!!.actions)
            }
        }
    }

    override val dataFormat: DataFormat
        get() = BusObject.DATA_FORMAT

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
        }
    }

    override fun boxLayout(obj: MixerFlow.MixerComponent, header: Region, content: Node?): Node {
        return createFaderBox(obj.sourceBus, obj.volume, obj)
    }

    private fun createFaderBox(
        bus: ReactiveValue<BusReference>, volume: ReactiveVariable<Decimal>, obj: MixerFlow.MixerComponent?,
    ): VBox {
        val namePane = createVerticalNameLabel(bus)
        val fader = createFader(volume)
        val volumeBox = createVolumeBox(volume, bus)
        val scopeButton = ServerActions.scopeBus.withContext(bus).makeButton("medium-icon-button")

        fader.focusedProperty().addListener { _, _, focused ->
            if (focused) {
                if (obj != null) {
                    channelsList!!.select(obj)
                    channelsList!!.getBox(obj).requestFocus()
                } else {
                    masterFaderBox!!.requestFocus()
                }
            }
        }
        fader.showTickLabelsProperty().addListener { _, _, showTickLabels ->
            if (!showTickLabels) {
                fader.isShowTickLabels = true
                fader.isShowTickMarks = true
            }
        }

        val (top, bottom) =
            if (obj != null) createPanKnobAndMuteSoloActionsBar(obj)
            else createMasterMonoAndMuteButtons()
        return VBox(
            HBox(infiniteSpace(), top, infiniteSpace()),
            Region() styleClass "fader-separator",
            scopeButton.centered(),
            volumeBox,
            HBox(hspace(5.0), namePane, infiniteSpace(), fader, hspace(5.0)).alwaysVGrow(),
            Region() styleClass "fader-separator",
            bottom
        ) styleClass "fader-layout"
    }

    private fun createMasterMonoAndMuteButtons(): Pair<HBox, StackPane> {
        val monoButton = monoAction.withContext(selectedMixer.get()!!)
            .makeTextButton("selector-button").styleClass("mono-button")
        val monoButtonPane = HBox(infiniteSpace(), monoButton, infiniteSpace()).centerChildren()
        monoButtonPane.prefHeight = 41.0

        val masterMuteButton = masterMuteAction.withContext(selectedMixer.get()!!).makeButton("medium-icon-button")

        return Pair(monoButtonPane, masterMuteButton.centered())
    }

    private fun createPanKnobAndMuteSoloActionsBar(obj: MixerFlow.MixerComponent): Pair<Knob, StackPane> {
        val panKnob = listConfig.createPanKnob(obj.pan, radius = 20.0)

        val muteAndSolo = ActionBar(
            MixerComponentListConfig.muteAndSolo.withContext(obj),
            buttonStyle = "mute-solo-button"
        )

        return Pair(panKnob, muteAndSolo.centered())
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
        nameLabel.fill = Color.WHITE
        nameLabel.rotate = -90.0
        StackPane.setAlignment(nameLabel, Pos.BOTTOM_CENTER)
        val namePane = StackPane(nameLabel).setFixedWidth(15.0)
        nameLabel.translateYProperty().bind(nameLabel.textProperty().map { -nameLabel.prefWidth(-1.0) / 2 })
        return namePane
    }

    private fun createFader(volume: ReactiveVariable<Decimal>): Slider {
        val fader = Slider(MixerFlow.MIN_VOLUME, MixerFlow.MAX_VOLUME, 0.0) styleClass "volume-fader"
        fader.userData = volume.forEach { v ->
            fader.value = v.toDouble()
        }
        fader.valueProperty().addListener { _, _, v ->
            setVolume(volume, v.toDouble().withPrecision(1))
        }
        return fader
    }

    override fun getContent(obj: MixerFlow.MixerComponent, mode: ObjectListView.DisplayMode): Parent = Region()

    private fun allMixerFlows() = context.project.flows.allFlows().filterIsInstance<MixerFlow>()

    private inner class MixerListPopup : SearchableListView<ObjectReference<MixerFlow>>("Select mixer") {
        override fun options(): List<ObjectReference<MixerFlow>> =
            allMixerFlows().map { f -> f.reference() } + ObjectReference.none()

        override fun createCell(option: ObjectReference<MixerFlow>): Region =
            HBox(label(option.name) styleClass "option-label")

        override fun extractText(option: ObjectReference<MixerFlow>): String = option.name.now
    }

    companion object : Type(15, "Mixer") {
        override val defaultSide: Side
            get() = Side.BOTTOM
        override val icon: Ikon
            get() = MaterialDesignT.TUNE_VERTICAL

        override val shortcuts: Array<String>
            get() = arrayOf("F10")

        override fun createToolPane(project: PonticelloProject): ToolPane = MixerPane(MixerComponentListConfig())

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
    }
}