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
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Text
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import org.kordamp.ikonli.materialdesign2.MaterialDesignV
import ponticello.impl.Decimal
import ponticello.impl.withPrecision
import ponticello.model.flow.MixerFlow
import ponticello.model.flow.MixerFlow.Companion.VOLUME_SPEC
import ponticello.model.obj.BusReference
import ponticello.model.obj.project
import ponticello.model.project.PonticelloProject
import ponticello.model.project.flows
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.ui.actions.ServerActions
import ponticello.ui.actions.undoable
import ponticello.ui.controls.DecimalPrompt
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

    private var channelsList: ObjectListView<MixerFlow.MixerComponent>? = null

    override val content: Parent
        get() {
            val mixer = selectedMixer.get()
            return if (mixer != null) {
                val masterFader = createFaderBox(
                    mixer.targetBus, mixer.masterVolume, obj = null,
                )
                val box = (object : Control() {}).styleClass("object-box")
                box.setRoot(masterFader)
                box.setPseudoClassState("inline-content", true)
                HBox(10.0, channelsList, box).alwaysVGrow()
            } else BorderPane(Label("No mixer selected"))
        }

    private var selectedMixer: ObjectReference<MixerFlow> = ObjectReference.none()
        set(value) {
            if (field == value) return
            field = value
            val mixer = value.get()
            listConfig.setMixer(mixer)
            channelsList = mixer?.let { m -> ObjectListView(m.components, this) }
            relayout()
        }

    override val headerContent: Node
        get() = MixerListPopup().selectorButton(this::selectedMixer, actionDescription = "Select mixer")

    override val headerActions: List<ContextualizedAction>
        get() = channelsList?.actions.orEmpty()

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
        registerShortcuts {
            registerActions(volumeActions.withContext(this@MixerPane))
        }
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
        val nameLabel = Text()
        nameLabel.textProperty().bind(bus.flatMap(BusReference::name).asObservableValue())
        nameLabel.fill = Color.WHITE
        nameLabel.rotate = -90.0
        StackPane.setAlignment(nameLabel, Pos.BOTTOM_CENTER)
        val namePane = StackPane(nameLabel).setFixedWidth(15.0)
        nameLabel.translateYProperty().bind(nameLabel.textProperty().map { -nameLabel.prefWidth(-1.0) / 2 })

        val fader = Slider(MixerFlow.MIN_VOLUME, MixerFlow.MAX_VOLUME, 0.0) styleClass "volume-fader"
        fader.userData = volume.forEach { v ->
            fader.value = v.toDouble()
        }
        fader.valueProperty().addListener { _, _, v ->
            VariableEdit.updateVariable(
                volume, v.toDouble().withPrecision(1),
                context[UndoManager], "Change volume"
            )
        }
        fader.orientation = Orientation.VERTICAL

        val valueLabel = label(volume.map { v -> "$v db" }) styleClass "fader-volume"
        val volumeBox = HBox(infiniteSpace(), valueLabel, infiniteSpace()) styleClass "fader-volume-box"
        volumeBox.setOnMouseClicked { ev ->
            val title = "Volume of ${bus.get().name.now}"
            volume.now = DecimalPrompt(title, volume.now, VOLUME_SPEC.range)
                .showDialog(ev) ?: return@setOnMouseClicked
        }
        setMargin(volumeBox, Insets(0.0, 3.0, 0.0, 3.0))

        val scopeButton = ServerActions.scopeBus.withContext(bus).makeButton("medium-icon-button")

        val (top, bottom) = if (obj != null) {
            val panKnob = listConfig.createPanKnob(obj.pan, radius = 20.0)

            val muteAndSolo = ActionBar(
                MixerComponentListConfig.muteAndSolo.withContext(obj),
                buttonStyle = "mute-solo-button"
            )

            Pair(panKnob, muteAndSolo.centered())
        } else {
            val monoButton = monoAction.withContext(selectedMixer.get()!!)
                .makeTextButton("selector-button").styleClass("mono-button")
            val monoButtonPane = HBox(infiniteSpace(), monoButton, infiniteSpace()).centerChildren()
            monoButtonPane.prefHeight = 41.0

            val masterMuteButton = masterMuteAction.withContext(selectedMixer.get()!!).makeButton("medium-icon-button")

            Pair(monoButtonPane, masterMuteButton.centered())
        }
        val layout = VBox(
            3.0,
            HBox(infiniteSpace(), top, infiniteSpace()),
            Region() styleClass "fader-separator",
            HBox(infiniteSpace(), scopeButton, infiniteSpace()),
            volumeBox,
            HBox(hspace(5.0), namePane, infiniteSpace(), fader, hspace(5.0)).alwaysVGrow(),
            Region() styleClass "fader-separator",
            bottom
        )
        layout.padding = Insets(5.0, 0.0, 5.0, 0.0)
        layout.prefWidth = 60.0
        return layout.centerChildren()
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

        private val volumeActions = collectActions<MixerPane> {
            addAction("Increase volume") {
                shortcut("UP")
                executes { pane ->
                    val channelsList = pane.channelsList ?: return@executes
                    val selected = channelsList.selectedObject() ?: return@executes
                    selected.volume.now = (selected.volume.now + VOLUME_SPEC.step.get()).coerceIn(VOLUME_SPEC.range)
                }
            }
            addAction("Decrease volume") {
                shortcut("DOWN")
                executes { pane ->
                    val channelsList = pane.channelsList ?: return@executes
                    val selected = channelsList.selectedObject() ?: return@executes
                    selected.volume.now = (selected.volume.now - VOLUME_SPEC.step.get()).coerceIn(VOLUME_SPEC.range)
                }
            }
        }
    }
}