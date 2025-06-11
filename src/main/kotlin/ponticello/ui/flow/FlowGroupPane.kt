package ponticello.ui.flow

import bundles.createBundle
import fxutils.*
import fxutils.actions.*
import fxutils.prompt.InfoPrompt
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Slider
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.input.TransferMode.COPY_OR_MOVE
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.*
import ponticello.model.flow.*
import ponticello.model.obj.InstrumentObject
import ponticello.model.obj.withName
import ponticello.model.registry.InstrumentRegistry
import ponticello.sc.Rate
import ponticello.sc.editor.BusSelector
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.actions.ServerActions
import ponticello.ui.controls.NameControl
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.colorPicker
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

class FlowGroupPane(private val group: AudioFlowGroup, ownWindow: Boolean): VBox(), ObjectListDisplayConfig<AudioFlow> {
    val flowsView = ObjectListView(group.flows, this)

    init {
        if (ownWindow) {
            val nameControl = NameControl(group).setFixedWidth(150.0)
            val colorPicker = colorPicker(group.associatedColor).setFixedWidth(30.0)
            val actions = AudioFlowPane.actions.withContext(group) + removeAction.withContext(group)
            val actionBar = ActionBar(actions, buttonStyle = "medium-icon-button")
            val header = HBox(5.0, nameControl, colorPicker, infiniteSpace(), actionBar).centerChildren()
            flowsView.autoResizeScene = true
            children.addAll(header, flowsView)
        } else {
            children.add(flowsView)
        }
    }

    private val context get() = group.context

    override val buttonStyle: String
        get() = "small-icon-button"

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Inline, DisplayMode.DetailsPane)

    override val dataFormat: DataFormat
        get() = AudioFlow.DATA_FORMAT

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> = when {
        dragboard.hasContent(AudioFlow.DATA_FORMAT) -> COPY_OR_MOVE
        dragboard.hasContent(InstrumentObject.DATA_FORMAT) -> arrayOf(TransferMode.LINK)
        else -> emptyArray()
    }

    override fun getDroppedObject(ev: DragEvent): AudioFlow? {
        return when {
            ev.dragboard.hasContent(AudioFlow.DATA_FORMAT) -> {
                val reference = ev.dragboard.getContent(AudioFlow.DATA_FORMAT) as AudioFlows.FlowReference
                val flow = reference.getFlow(context[AudioFlows]) ?: return null
                if (TransferMode.COPY != ev.acceptedTransferMode) flow
                else {
                    val copy = flow.copy().withName("${flow.name.now}_copy")
                    copy.setActive(flow.isActive.now)
                    copy
                }
            }
            ev.dragboard.hasContent(InstrumentObject.DATA_FORMAT) -> {
                val registry = context[InstrumentRegistry]
                val def = ev.dragboard.getFrom(registry, InstrumentObject.DATA_FORMAT) ?: return null
                val anchor = Point2D(ev.screenX, ev.screenY)
                val controls = ScorePane.getInitialControls(def, context, defaultBus = null, anchor) ?: return null
                return InstrumentFlow.create(def, controls)
            }
            else -> null
        }
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
        is InstrumentFlow -> ParameterControlsPane(obj)
        is UtilityFlow -> Slider(-60.0, +6.0, 0.0) styleClass "volume-fader"
        is MixerFlow -> MixerFlowView(obj)
        else -> null
    }

    override fun getActions(box: ObjectBox<AudioFlow>): List<ContextualizedAction> {
        return actions.withContext(box.obj)
    }

    override fun configureDragboard(obj: AudioFlow, dragboard: Dragboard) {
        val ref = AudioFlows.FlowReference(group.name.now, obj.name.now)
        dragboard.setContent(mapOf(AudioFlow.DATA_FORMAT to ref))
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
                    pane.showContent(flow.def)
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

        private val removeAction = action<AudioFlowGroup>("Remove") {
            icon(MaterialDesignD.DELETE)
            executes { group -> group.context[AudioFlows].remove(group) }
        }
    }
}