package ponticello.ui.flow

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.prompt.SearchableListView
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Text
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import ponticello.impl.withPrecision
import ponticello.model.flow.MixerFlow
import ponticello.model.obj.BusReference
import ponticello.model.obj.project
import ponticello.model.project.PonticelloProject
import ponticello.model.project.flows
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.ui.dock.MixerPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.registry.ListDisplayConfig
import ponticello.ui.registry.ObjectListView
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable

class MixerPane(
    private val listConfig: MixerComponentListConfig,
) : ToolPane(), ListDisplayConfig<MixerFlow.MixerComponent> by listConfig {
    override val type: Type
        get() = MixerPane
    override var content: Parent = BorderPane(Label("No mixer selected"))
        private set(value) {
            children.replace(field, value)
            field = value
        }

    private val selectedMixer: ReactiveVariable<ObjectReference<MixerFlow>> = reactiveVariable(ObjectReference.none())

    override val headerContent: Node = MixerListPopup().selectorButton(selectedMixer, actionDescription = "Select mixer")

    private fun allMixerFlows() = context.project.flows.allFlows().filterIsInstance<MixerFlow>()

    private lateinit var mixerObserver: Observer

    override val headerActions: List<ContextualizedAction>
        get() = emptyList()

    override val boxStyle: Array<String>
        get() = arrayOf("object-box", "mixer-strip-box")

    override val supportedModes: Collection<ObjectListView.DisplayMode>
        get() = setOf(ObjectListView.DisplayMode.Inline(collapsable = false))

    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override fun defaultState(): ToolPaneState = MixerPaneState.default()

    override fun afterSetup() {
        mixerObserver = selectedMixer.observe { _, _, ref ->
            val mixer = ref.get()
            if (mixer != null) {
                listConfig.setMixer(mixer)
                content = ObjectListView(mixer.components, this).also { it.alwaysVGrow() }
            } else {
                content = BorderPane(Label("No mixer selected"))
            }

        }
        val state = initialState
        if (state is MixerPaneState) {
            state.flowReference.resolve(allMixerFlows())
            selectedMixer.now = state.flowReference
        }
    }

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is MixerPaneState) {
            dest.flowReference = selectedMixer.now
        }
    }

    override fun boxLayout(obj: MixerFlow.MixerComponent, header: Region, content: Node?): Node {
        val nameLabel = Text()
        nameLabel.textProperty().bind(obj.sourceBus.flatMap(BusReference::name).asObservableValue())
        nameLabel.fill = Color.WHITE
        nameLabel.rotate = -90.0
//        nameLabel.prefWidth = 15.0
//        nameLabel.translateXProperty().bind(nameLabel.widthProperty().divide(2))
//        nameLabel.translateYProperty().bind(nameLabel.widthProperty().divide(2))

        val fader = Slider(MixerFlow.MIN_VOLUME, MixerFlow.MAX_VOLUME, 0.0)
        fader.userData = obj.volume.forEach { v ->
            fader.value = v.toDouble()
        }
        fader.valueProperty().addListener { _, _, v ->
            obj.volume.set(v.toDouble().withPrecision(1))
        }
        fader.orientation = Orientation.VERTICAL
//        nameLabel.prefHeightProperty().bind(fader.heightProperty())

        val valueLabel = label(obj.volume.map { v -> "$v db" }) styleClass "fader-volume"

        val panKnob = listConfig.createPanKnob(obj, 16.0)
        val muteAndSolo = ActionBar(MixerComponentListConfig.muteAndSolo.withContext(obj), "medium-icon-button")

        StackPane.setAlignment(nameLabel, Pos.BOTTOM_CENTER)
        val namePane = StackPane(nameLabel).setFixedWidth(15.0)
        nameLabel.translateYProperty().bind(nameLabel.textProperty().map { -nameLabel.prefWidth(-1.0) / 2 })

        return VBox(
            5.0,
            HBox(infiniteSpace(),  panKnob, infiniteSpace()),
            Region() styleClass "fader-separator",
            valueLabel,
            HBox(hspace(5.0), namePane, hspace(5.0), fader, infiniteSpace()).alwaysVGrow(),
            Region() styleClass "fader-separator",
            muteAndSolo
        ).centerChildren() styleClass "mixer-strip-box"
    }

    override fun getContent(obj: MixerFlow.MixerComponent, mode: ObjectListView.DisplayMode): Parent = Region()

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
    }
}