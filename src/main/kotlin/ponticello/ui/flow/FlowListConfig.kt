package ponticello.ui.flow

import bundles.createBundle
import fxutils.actions.Action
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.InfoPrompt
import fxutils.styleClass
import fxutils.widthAtLeast
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Slider
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.model.flow.*
import ponticello.model.obj.InstrumentObject
import ponticello.model.obj.withName
import ponticello.model.registry.InstrumentRegistry
import ponticello.sc.Rate
import ponticello.sc.editor.BusSelector
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.actions.ServerActions
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.getFrom
import ponticello.ui.registry.InstrumentRegistryPane
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListDisplayConfig
import ponticello.ui.registry.ObjectListView
import ponticello.ui.registry.ObjectListView.DisplayMode
import ponticello.ui.score.ParameterControlsPane
import ponticello.ui.score.ScorePane
import reaktive.value.binding.binding
import reaktive.value.now
import reaktive.value.reactiveValue

class FlowListConfig(
    private val group: AudioFlowGroup,
    private val autoResizeScene: Boolean,
) : ObjectListDisplayConfig<AudioFlow> {
    private val context get() = group.context

    override val buttonStyle: String
        get() = "small-icon-button"
    override val enableReordering: Boolean
        get() = true

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Inline, DisplayMode.DetailsPane)

    fun canDrop(db: Dragboard): Boolean = when {
        db.hasContent(AudioFlow.DATA_FORMAT) -> true
        db.hasContent(InstrumentObject.DATA_FORMAT) ->
            db.getFrom(context[InstrumentRegistry], InstrumentObject.DATA_FORMAT) != null

        else -> false
    }

    override fun getItemContent(obj: AudioFlow): List<Node> = when (obj) {
        is MixerFlow -> {
            val selector = BusSelector()
            selector.setFilter(rate = Rate.Audio, channels = null)
            selector.syncWith(obj.targetBus)
            selector.initialize(context)
            val selectorControl = ObjectSelectorControl(selector, createBundle())
            listOf(selectorControl)
        }

        is VSTPluginFlow -> {
            val busSelector = ObjectSelectorControl(obj.busSelector, createBundle())
                .widthAtLeast(100.0)
            listOf(busSelector)
        }

        else -> emptyList()
    }

    override fun getContent(obj: AudioFlow, mode: DisplayMode): Parent? = when (obj) {
        is CodeFlow -> obj.codeEditor.control
        is SendFlow -> SendFlowView(obj)
        is InstrumentFlow -> ParameterControlsPane(obj).apply {
            listView.autoResizeScene = autoResizeScene
        }
        is UtilityFlow -> Slider(-60.0, +6.0, 0.0) styleClass "volume-fader"
        is MixerFlow -> MixerFlowView(obj).apply {
            componentsView.autoResizeScene = autoResizeScene
        }

        else -> null
    }

    override fun getActions(box: ObjectBox<AudioFlow>): List<ContextualizedAction> {
        return actions.withContext(box.obj)
    }

    override fun dataFormat(obj: AudioFlow): DataFormat = AudioFlow.DATA_FORMAT

    override fun configureDragboard(obj: AudioFlow, dragboard: Dragboard) {
        val ref = AudioFlows.FlowReference(group.name.now, obj.name.now)
        dragboard.setContent(mapOf(AudioFlow.DATA_FORMAT to ref))
    }

    fun onDrop(ev: DragEvent, listView: ObjectListView<AudioFlow>) {
        if (ev.dragboard.hasContent(AudioFlow.DATA_FORMAT)) {
            val reference = ev.dragboard.getContent(AudioFlow.DATA_FORMAT) as AudioFlows.FlowReference
            val flow = reference.getFlow(context[AudioFlows]) ?: return
            var newIndex = listView.getBoxes().binarySearchBy(ev.y) { n -> n.layoutY }
            if (newIndex < 0) newIndex = -(newIndex + 1)
            val copy = flow.copy().withName("${flow.name.now}_copy")
            listView.source.add(copy, newIndex)
            copy.setActive(flow.isActive.now)
            if (TransferMode.COPY !in ev.dragboard.transferModes) {
                reference.removeFrom(context[AudioFlows])
            }
        } else if (ev.dragboard.hasContent(InstrumentObject.DATA_FORMAT)) {
            val registry = context[InstrumentRegistry]
            val def = ev.dragboard.getFrom(registry, InstrumentObject.DATA_FORMAT) ?: return
            val anchor = Point2D(ev.x, ev.y)
            val controls = ScorePane.getInitialControls(def, context, defaultBus = null, anchor) ?: return
            val flow = InstrumentFlow.create(def, controls)
            listView.source.add(flow)
        }
    }

    companion object {
        private val actions = collectActions<AudioFlow> {
            addAction("Show VST editor") {
                icon(MaterialDesignE.EYE)
                executesOn<VSTPluginFlow> { flow -> flow.showEditor() }
            }
            addAction("View SynthDef") {
                icon(Material2AL.CODE)
                shortcut("Ctrl+L")
                enableWhen { flow ->
                    if (flow !is InstrumentFlow) reactiveValue(false)
                    else flow.instrumentSelector.isResolved
                }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                executes { flow ->
                    flow as InstrumentFlow
                    val pane = flow.context[AppLayout].get<InstrumentRegistryPane>()
                    pane.listView.showContent(flow.def)
                }
            }
            add(ServerActions.scopeBus) { f -> (f as? MixerFlow)?.targetBus }
            addAction("Toggle activated") {
                icon { flow ->
                    binding(flow.isValid, flow.isActive) { valid, active ->
                        when {
                            !valid -> MaterialDesignC.CLOSE_CIRCLE_OUTLINE
                            active -> MaterialDesignR.RADIOBOX_MARKED
                            else -> MaterialDesignR.RADIOBOX_BLANK
                        }
                    }
                }
                shortcut("Ctrl+T")
                executes { flow, ev ->
                    if (!flow.isValid.now) {
                        InfoPrompt("Cannot activate flow").showDialog(ev) //TODO better error description
                        return@executes
                    }
                    flow.toggleActive()
                }
            }
            addAction("Reload") {
                icon(MaterialDesignS.SYNC)
                shortcut("Ctrl+U")
                enableWhen { flow -> flow.isActive }
                executes { flow -> flow.sync() }
            }
        }
    }
}