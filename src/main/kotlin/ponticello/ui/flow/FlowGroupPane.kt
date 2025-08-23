package ponticello.ui.flow

import bundles.createBundle
import fxutils.*
import fxutils.actions.*
import fxutils.prompt.InfoPrompt
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.TextPrompt
import javafx.event.Event
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Slider
import javafx.scene.control.Tooltip
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.input.TransferMode.COPY_OR_MOVE
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.model.flow.*
import ponticello.model.obj.FlowReference
import ponticello.model.obj.InstrumentObject
import ponticello.model.obj.resolve
import ponticello.model.obj.withName
import ponticello.model.registry.InstrumentRegistry
import ponticello.model.registry.ObjectList
import ponticello.model.registry.reference
import ponticello.sc.Identifier
import ponticello.sc.Rate
import ponticello.sc.editor.BusSelector
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.actions.ServerActions
import ponticello.ui.controls.NameControl
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.colorPicker
import ponticello.ui.impl.getFrom
import ponticello.ui.midi.ContextualMidiReceiver
import ponticello.ui.registry.InstrumentRegistryPane
import ponticello.ui.registry.ListDisplayConfig
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView
import ponticello.ui.registry.ObjectListView.DisplayMode
import ponticello.ui.score.ParameterControlsPane
import ponticello.ui.score.ScorePane
import reaktive.value.binding.binding
import reaktive.value.binding.`if`
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveValue

class FlowGroupPane(
    private val group: AudioFlowGroup,
    private val parent: AudioFlowsPane?,
) : VBox(), ListDisplayConfig<AudioFlow> {
    val flowsView = ObjectListView(group.flows, this, scrollable = true)

    init {
        flowsView.itemsScrollPane.neverSquishHorizontally()
        if (parent == null) {
            val nameControl = NameControl(group).setFixedWidth(150.0)
            val colorPicker = colorPicker(group.associatedColor).setFixedWidth(30.0)
            val actions = flowsView.actions + toggleActiveAction.withContext(group)
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
        get() = setOf(DisplayMode.Inline(collapsable = true), DisplayMode.DetailsPane)

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
                val reference = ev.dragboard.getContent(AudioFlow.DATA_FORMAT) as FlowReference
                reference.resolve(context)
                val flow = reference.get() ?: return null
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

    override fun getHeaderContent(obj: AudioFlow): List<Node> = when (obj) {
        is MixerFlow -> {
            val selector = BusSelector()
            selector.setFilter(rate = Rate.Audio, channels = null)
            selector.syncWith(obj.targetBus)
            selector.initialize(context)
            val selectorControl = ObjectSelectorControl(selector, createBundle())
            listOf(selectorControl.widthAtLeast(100.0))
        }

        is VSTPluginFlow -> {
            val busSelector = ObjectSelectorControl(obj.busSelector, createBundle())
                .widthAtLeast(100.0)
            listOf(busSelector)
        }

        else -> emptyList()
    }

    override fun getContent(obj: AudioFlow, mode: DisplayMode): Parent = when (obj) {
        is CodeFlow -> obj.codeEditor.control
        is SendFlow -> SendFlowView(obj)
        is InstrumentFlow -> ParameterControlsPane(obj)
        is UtilityFlow -> Slider(-60.0, +6.0, 0.0) styleClass "volume-fader"
        is MixerFlow -> MixerFlowView.create(obj)
        is VSTPluginFlow -> VSTPluginFlowView(obj)
    }

    override fun configureBox(box: ObjectBox<AudioFlow>, currentMode: DisplayMode) {
        context[ContextualMidiReceiver].registerMidiContext(box) {
            box.obj.midiContext().takeIf { !box.isCollapsed.now }
        }
        if (box.obj is VSTPluginFlow) {
            val tooltip = Tooltip()
            tooltip.textProperty().bind(box.obj.pluginName.asObservableValue())
            Tooltip.install(box.header, tooltip)
        }
    }

    override fun getActions(box: ObjectBox<AudioFlow>): List<ContextualizedAction> {
        return flowActions.withContext(box.obj)
    }

    override fun configureDragboard(obj: AudioFlow, dragboard: Dragboard) {
        dragboard.setContent(mapOf(AudioFlow.DATA_FORMAT to obj.reference()))
    }

    override fun onDeselected(obj: AudioFlow) {
        if (obj !is InstrumentFlow) return
        val box = flowsView.getBox(obj)
        when (val content = box.content) {
            is ParameterControlsPane -> content.listView.deselectAll()
            is MixerFlowView -> content.componentsView.deselectAll()
        }
    }

    override fun createNewObject(ev: Event?, list: ObjectList<AudioFlow>): AudioFlow? {
        val options = FlowOption.getOptions(context)
        val option = SimpleSearchableListView(options, "Add flow").showPopup(ev) ?: return null
        val defaultName = option.defaultName()
        val takenFlowNames = context[AudioFlows].allFlows().mapTo(mutableSetOf()) { f -> f.name.now }
        val idx = (1..Int.MAX_VALUE).first { idx -> "${defaultName}_$idx" !in takenFlowNames }
        val name = FlowNamePrompt(takenFlowNames, "Flow name", "${defaultName}_$idx")
            .showDialog(ev) ?: return null
        val anchor = ev.popupAnchor()
        val flow = option.createFlow(context, anchor) ?: return null
        flow.setInitialName(name)
        return flow
    }

    override fun filter(obj: AudioFlow): Boolean = parent?.filter(obj) ?: true

    private class FlowNamePrompt(
        private val takenFlowNames: Set<String>,
        title: String, initialText: String,
    ) : TextPrompt<String>(title, initialText) {
        override fun convert(text: String): String? = text.takeIf { Identifier.isValid(it) && it !in takenFlowNames }
    }

    companion object {
        val toggleActiveAction = action<AudioFlowGroup>("Toggle activated") {
            description { grp ->
                `if`(
                    grp.isActive,
                    then = { "Deactivate group" },
                    otherwise = { "Activate group" }
                )
            }
            icon { grp ->
                grp.isActive.map { active ->
                    if (active) MaterialDesignR.RADIOBOX_MARKED
                    else MaterialDesignR.RADIOBOX_BLANK
                }
            }
            executes { grp -> grp.toggleActive() }
        }

        private val flowActions = collectActions<AudioFlow> {
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