package ponticello.ui.flow

import fxutils.*
import fxutils.actions.*
import fxutils.prompt.InfoPrompt
import fxutils.prompt.SimpleSelectorPrompt
import fxutils.prompt.TextPrompt
import javafx.event.Event
import javafx.geometry.Orientation
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Slider
import javafx.scene.control.Tooltip
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.materialdesign2.*
import ponticello.model.flow.*
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.InstrumentRegistry
import ponticello.model.obj.FlowReference
import ponticello.model.obj.resolve
import ponticello.model.obj.withName
import ponticello.model.registry.ObjectList
import ponticello.model.registry.reference
import ponticello.sc.Identifier
import ponticello.ui.actions.ServerActions
import ponticello.ui.controls.NameControl
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.colorPicker
import ponticello.ui.impl.getFrom
import ponticello.ui.midi.MidiContext
import ponticello.ui.registry.InstrumentRegistryPane
import ponticello.ui.registry.ListDisplayConfig
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView
import ponticello.ui.registry.ObjectListView.DisplayMode
import ponticello.ui.score.ParameterControlsPane
import ponticello.ui.score.SoundProcessView
import reaktive.value.binding.binding
import reaktive.value.binding.flatMap
import reaktive.value.binding.`if`
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveValue
import java.util.*

class FlowGroupPane(
    private val group: AudioFlowGroup,
    private val parent: AudioFlowsPane?,
) : VBox(), ListDisplayConfig<AudioFlow> {
    val flowsView = ObjectListView(group.flows, this, scrollable = true)
    private var midiContexts: MutableMap<AudioFlow, MidiContext?>? = null

    init {
        flowsView.alwaysVGrow()
        alwaysVGrow()
        val addFlowButton = addFlowAction.withContext(this).makeButton("small-icon-button")
        val layout = HBox(VBox(infiniteSpace(), addFlowButton, infiniteSpace()), flowsView).alwaysVGrow()
        if (parent == null) {
            val nameControl = NameControl(group).setFixedWidth(150.0)
            val colorPicker = colorPicker(group.associatedColor).setFixedWidth(30.0)
            val actions = flowsView.actions + toggleActiveAction.withContext(group)
            val actionBar = ActionBar(actions, buttonStyle = "medium-icon-button")
            val header = HBox(5.0, nameControl, colorPicker, infiniteSpace(), actionBar).centerChildren()
            flowsView.autoResizeScene = true
            children.addAll(header, layout)
        } else {
            children.add(layout)
        }
    }

    private val context get() = group.context

    override val nameDisplayWidth: Double
        get() = 130.0

    override val canDuplicate: Boolean
        get() = true

    override val buttonStyle: String
        get() = "small-icon-button"

    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Collapsable, DisplayMode.DetailsPane)

    override val dataFormat: DataFormat
        get() = AudioFlow.DATA_FORMAT

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> = when {
        dragboard.hasContent(InstrumentObject.DATA_FORMAT) -> arrayOf(TransferMode.LINK)
        else -> super.acceptedTransferModes(dragboard)
    }

    override fun getDroppedObjects(ev: DragEvent, targetView: ObjectListView<AudioFlow>): List<AudioFlow> {
        return when {
            ev.dragboard.hasContent(AudioFlow.DATA_FORMAT) -> {
                @Suppress("UNCHECKED_CAST")
                val reference = ev.dragboard.getContent(AudioFlow.DATA_FORMAT) as FlowReference
                reference.resolve(context)
                val flow = reference.get() ?: return emptyList()
                if (TransferMode.COPY != ev.acceptedTransferMode) listOf(flow)
                else {
                    val takenFlowNames = context[AudioFlows].allFlows().mapTo(mutableSetOf()) { f -> f.name.now }
                    val defaultName = flow.name.now + "_copy"
                    val name = FlowNamePrompt(takenFlowNames, "Name for duplicate", defaultName)
                        .showDialog(ev) ?: return emptyList()
                    val copy = flow.copy().withName(name)
                    copy.setActive(flow.isActive.now)
                    listOf(copy)
                }
            }

            ev.dragboard.hasContent(InstrumentObject.DATA_FORMAT) -> {
                val registry = context[InstrumentRegistry]
                val def = ev.dragboard.getFrom(registry, InstrumentObject.DATA_FORMAT) ?: return emptyList()
                val anchor = Point2D(ev.screenX, ev.screenY)
                val controls = SoundProcessView.getInitialControls(
                    def, context, defaultBus = null, anchor
                ) ?: return emptyList()
                return listOf(InstrumentFlow.create(def, controls))
            }

            else -> emptyList()
        }
    }

    override fun defaultInsertionIndex(source: List<AudioFlow>): Int = 0

    override fun getContent(obj: AudioFlow, box: ObjectBox<AudioFlow>): Parent = when (obj) {
        is CodeFlow -> obj.codeEditor.control
        is SendFlow -> SendFlowView(obj)
        is InstrumentFlow -> ParameterControlsPane(obj, midiContext = midiContext(obj)).pad(5.0)
        is UtilityFlow -> Slider(-60.0, +6.0, 0.0) styleClass "volume-fader"
        is MixerFlow -> MixerFlowView.create(obj)
        is VSTPluginFlow -> VSTPluginFlowView(obj)
    }

    override fun collapsedLayout(box: ObjectBox<AudioFlow>): Node {
        val nameLabel = Text()
        nameLabel.textProperty().bind(box.obj.name.asObservableValue())
        val namePane = makeVerticalLabel(nameLabel).alwaysVGrow()
        val actions = listOf(
            expandFlowAction.withContext(box),
            removeFlowAction.withContext(box)
        ) + getActions(box).reversed()
        val actionBar = ActionBar(actions, "small-icon-button")
        actionBar.setOrientation(Orientation.VERTICAL)
        nameLabel.setOnMouseClicked { ev ->
            if (ev.clickCount == 2) {
                box.toggleExpanded()
            }
        }
        box.setupDragging(dragTarget = box)
        return VBox(actionBar, namePane).pad(3.0)
    }

    override fun createSeparatorNode(box: ObjectBox<AudioFlow>): VBox {
        val actionBar = ActionBar(separatorBarActions.withContext(box), "small-icon-button")
        return VBox(infiniteSpace(), actionBar, infiniteSpace())
    }

    private fun midiContext(obj: AudioFlow): MidiContext? {
        if (midiContexts == null) midiContexts = WeakHashMap()
        return midiContexts!!.getOrPut(obj) { obj.midiContext() }
    }

    override fun configureBox(box: ObjectBox<AudioFlow>, currentMode: DisplayMode) {
        val midiContext = midiContext(box.obj)
        if (midiContext != null) {
            midiContext.setCondition {
                val showing = context[AppLayout].get<AudioFlowsPane>().isShowing.now
                val groupExpanded = parent == null || parent.listView.getBox(group).isExpanded
                showing && groupExpanded && box.isExpanded
            }
            box.registerShortcuts(
                listOf(MidiContext.toggleActiveAction.withContext(midiContext))
            )
        }
        box.setOnMouseClicked { box.requestFocus() }
        if (box.obj is VSTPluginFlow) {
            val tooltip = Tooltip()
            tooltip.textProperty().bind(box.obj.pluginName.asObservableValue())
            Tooltip.install(box.header, tooltip)
        }
    }

    override fun getActions(box: ObjectBox<AudioFlow>): List<ContextualizedAction> = flowActions.withContext(box.obj)

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
        val option = SimpleSelectorPrompt(options, "Add flow").showPopup(ev) ?: return null
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

        private val expandFlowAction = action<ObjectBox<AudioFlow>>("Expand") {
            icon(MaterialDesignC.CHEVRON_RIGHT)
            executes { box -> box.setExpanded(true) }
        }

        private val separatorBarActions = collectActions<ObjectBox<AudioFlow>> {
            addAction("Insert flow") {
                icon(MaterialDesignP.PLUS)
                executes { box, ev ->
                    val flowsView = box.getParent<FlowGroupPane>()?.flowsView ?: return@executes
                    val idx = flowsView.source.indexOf(box.obj) + 1
                    flowsView.addObject(ev, idx)
                }
            }
        }

        private val addFlowAction = action<FlowGroupPane>("Add flow") {
            icon(MaterialDesignP.PLUS)
            executes { pane, ev ->
                pane.flowsView.addObject(ev, idx = 0)
            }
        }

        private val removeFlowAction = action<ObjectBox<AudioFlow>>("Remove") {
            icon(MaterialDesignC.CLOSE_CIRCLE_OUTLINE)
            shortcut("DELETE")
            executes { box -> box.parent.source.remove(box.obj) }
        }

        private val flowActions = collectActions<AudioFlow> {
            addAction("Show VST editor") {
                icon(MaterialDesignE.EYE)
                executesOn<VSTPluginFlow> { flow -> flow.showEditor() }
            }
            addAction("View Instrument") {
                icon(Codicons.CODE)
                shortcut("Alt+I")
                description { flow ->
                    if (flow is InstrumentFlow)
                        flow.instrumentSelector.result.flatMap { instr ->
                            instr.name.map { n -> "View instrument: $n" }
                        }
                    else reactiveValue("<no instrument>")
                }
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