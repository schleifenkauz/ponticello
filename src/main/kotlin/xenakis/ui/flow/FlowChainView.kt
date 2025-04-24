package xenakis.ui.flow

import bundles.createBundle
import fxutils.actions.Action
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.registerShortcuts
import fxutils.centerChildren
import fxutils.prompt.SimpleSearchableListView
import fxutils.setupDropArea
import fxutils.styleClass
import javafx.scene.Parent
import javafx.scene.control.Slider
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.ReactiveString
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.toDecimal
import xenakis.model.flow.*
import xenakis.model.obj.BusObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.project.flows
import xenakis.model.registry.SynthDefRegistry
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.editor.BusSelector
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.controls.Knob
import xenakis.ui.impl.getFrom
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.midi.ContextualMidiReceiver
import xenakis.ui.midi.ParameterControlsMidiContext
import xenakis.ui.registry.NamedObjectListView
import xenakis.ui.registry.NamedObjectListView.DisplayMode
import xenakis.ui.registry.ObjectBox
import xenakis.ui.registry.ObjectBoxConfig
import xenakis.ui.score.ParameterControlsPane

class FlowChainView(
    private val flows: AudioFlows,
    private val associatedBus: BusObject,
) : VBox(5.0), ObjectBoxConfig<AudioFlow> {
    private val busBox = BusObjectBox(associatedBus)

    private val myFlows = flows.associatedFlows(associatedBus)
    private val flowsList = NamedObjectListView(myFlows, this)

    override val buttonStyle: String
        get() = "flow-action-button"
    override val enableReordering: Boolean
        get() = true
    override val enableAddObjectButton: Boolean
        get() = true

    override val centerAddObjectButton: Boolean
        get() = true

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Inline, DisplayMode.SubWindow)

    init {
        children.addAll(flowsList, busBox)
        setVgrow(flowsList, Priority.ALWAYS)
        setupDropArea(::canDrop, ::onDrop)
        registerShortcuts(flowsList.actions)
    }

    private fun canDrop(db: Dragboard): Boolean = when {
        db.hasContent(AudioFlow.DATA_FORMAT) -> true
        db.hasContent(SynthDefObject.DATA_FORMAT) ->
            db.getFrom(flows.context[SynthDefRegistry], SynthDefObject.DATA_FORMAT) != null

        else -> false
    }

    override fun getContent(obj: AudioFlow): Parent? = when (obj) {
        is CodeFlow -> obj.codeEditor.control
        is ScoreObjectPlaceholder -> null
        is SendFlow -> {
            val spec = NumericalControlSpec(100.0, 0.0, 100.0, 1.toDecimal(), Warp.Linear, Color.GREEN)
            val knob = Knob("Amount", (obj.amountPercent), spec, color = Color.gray(0.3))
            val targetBusSelector = BusSelector()
            targetBusSelector.setFilter(reactiveValue(obj.associatedBus.rate), obj.associatedBus.channels)
            targetBusSelector.syncWith(obj.targetRef)
            targetBusSelector.initialize(obj.context)
            val selectorControl = ObjectSelectorControl(targetBusSelector, createBundle())
            HBox(10.0, knob, selectorControl).centerChildren()
        }

        is SynthFlow -> ParameterControlsPane(obj, "Flow controls")
        is UtilityFlow -> Slider(-60.0, +6.0, 0.0) styleClass "volume-fader"
    }

    override fun getActions(box: ObjectBox<AudioFlow>): List<ContextualizedAction> {
        val extraActions = when (box.obj) {
            is SynthFlow -> synthFlowActions.withContext(box.obj)
            else -> emptyList()
        }
        return extraActions + defaultActions.withContext(box.obj)
    }

    override fun onSelected(obj: AudioFlow) {
        when (obj) {
            is SynthFlow -> {
                val receiver = obj.context[ContextualMidiReceiver]
                val context = ParameterControlsMidiContext(obj.controls)
                receiver.setContext(context)
            }

            is UtilityFlow -> {} //TODO for the volume fader a motorized fader could be integrated...!
            else -> {}
        }
    }

    override fun dataFormat(obj: AudioFlow): DataFormat = AudioFlow.DATA_FORMAT

    override fun getDefaultDisplayName(obj: AudioFlow): ReactiveString = obj.getDefaultName()

    private fun onDrop(ev: DragEvent) {
        if (ev.dragboard.hasContent(AudioFlow.DATA_FORMAT)) {
            val reference = ev.dragboard.getContent(AudioFlow.DATA_FORMAT) as AudioFlows.FlowReference
            val flow = reference.getFrom(flows)
            var newIndex = flowsList.getBoxes().binarySearchBy(ev.y) { n -> n.layoutY }
            if (newIndex < 0) newIndex = -(newIndex + 1)
            if (flow.associatedBus != associatedBus) {
                val copy = flow.copy()
                copy.initialize(flows.context, associatedBus)
                flows.addFlow(copy, newIndex)
                if (TransferMode.COPY !in ev.dragboard.transferModes) {
                    flows.removeFlow(flow)
                }
            } else {
                if (TransferMode.MOVE !in ev.dragboard.transferModes) return
                flow.context[currentProject].flows.moveFlow(flow, newIndex)
            }
        } else if (ev.dragboard.hasContent(SynthDefObject.DATA_FORMAT)) {
            val registry = flows.context[SynthDefRegistry]
            val def = ev.dragboard.getFrom(registry, SynthDefObject.DATA_FORMAT) ?: return
            val flow = SynthFlow.createFor(associatedBus, def, flows.context)
            flows.addFlow(flow)
        }
    }

    override fun createNewObject(): AudioFlow? {
        val options = FlowOption.getOptions(flows.context)
        val option = SimpleSearchableListView(options, "Add flow").showPopup(anchorNode = flowsList) ?: return null
        return option.createFlow(flows.context, anchor = flowsList, associatedBus)
    }

    companion object {
        private val defaultActions = collectActions<AudioFlow> {
            addAction("Toggle activated") {
                icon { flow ->
                    flow.isActive.map { active ->
                        if (active) MaterialDesignR.RADIOBOX_MARKED
                        else MaterialDesignR.RADIOBOX_BLANK
                    }
                }
                applicableIf { flow -> flow.canDeactivate }
                shortcut("Ctrl+T")
                executes { flow -> flow.isActive.now = !flow.isActive.now }
            }
        }

        private val synthFlowActions = collectActions<SynthFlow> {
            addAction("View SynthDef") {
                icon(Material2AL.CODE)
                shortcut("Ctrl+L")
                applicableWhen { flow -> flow.synthDefSelector.isResolved }
                ifNotApplicable(Action.IfNotApplicable.Disable)
                executes { flow ->
                    val pane = flow.context[XenakisMainActivity].synthDefsPane
                    pane.listView.showContent(flow.synthDef)
                }
            }
        }

    }
}