package xenakis.ui.flow

import bundles.createBundle
import fxutils.actions.*
import fxutils.centerChildren
import fxutils.label
import fxutils.prompt.SimpleSearchableListView
import fxutils.setupDropArea
import fxutils.styleClass
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.control.ScrollPane.ScrollBarPolicy
import javafx.scene.control.Slider
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.*
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.toDecimal
import xenakis.model.flow.*
import xenakis.model.obj.BusObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.score.KnobControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.editor.BusSelector
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.controls.ControlAssignmentView
import xenakis.ui.controls.Knob
import xenakis.ui.impl.getFrom
import xenakis.ui.launcher.XenakisLauncher
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.registry.ObjectBoxList
import xenakis.ui.registry.ObjectBoxSource
import xenakis.ui.score.ParameterizedScoreObjectView

class VerticalFlowsBox(
    private val flows: AudioFlows,
    private val associatedBus: BusObject
) : VBox(5.0), ObjectBoxSource<AudioFlow> {
    private val busBox = BusObjectBox(associatedBus)

    val flowsList = ObjectBoxList(this)

    override val items: List<AudioFlow>
        get() = flows.associatedFlows(associatedBus)

    override val buttonStyle: String
        get() = "flow-action-button"
    override val orientation: Orientation
        get() = Orientation.VERTICAL
    override val enableReordering: Boolean
        get() = true

    init {
        val addFlowRegion = makeAddFlowButton()
        flowsList.isFitToWidth = true
        flowsList.hbarPolicy = ScrollBarPolicy.NEVER
        children.addAll(flowsList, addFlowRegion, busBox)
        setVgrow(addFlowRegion, Priority.ALWAYS)
        setupDropArea({ db -> db.hasContent(AudioFlow.DATA_FORMAT) }, ::onDrop)
        registerShortcuts(flowsList.actions)
    }

    override fun getContent(obj: AudioFlow): List<Node> {
        val content = when (obj) {
            is CodeFlow -> obj.codeEditor.control
            is ScoreObjectPlaceholder -> label(obj.group.name.map { "" })
            is SendFlow -> {
                val ctrl = KnobControl(obj.amountPercent)
                val spec = NumericalControlSpec(100.0, 0.0, 100.0, 1.toDecimal(), Warp.Linear)
                val knob = Knob("Amount", ctrl, spec, obj.context)
                val targetBusSelector = BusSelector()
                targetBusSelector.setFilter(reactiveValue(obj.associatedBus.rate), obj.associatedBus.channels)
                targetBusSelector.syncWith(obj.targetRef)
                targetBusSelector.initialize(obj.context)
                val selectorControl = ObjectSelectorControl(targetBusSelector, createBundle())
                HBox(10.0, knob, selectorControl).centerChildren()
            }

            is SynthFlow -> ControlAssignmentView(obj)
            is UtilityFlow -> Slider(-60.0, +6.0, 0.0) styleClass "volume-fader"
        }
        return listOf(content)
    }

    override fun getActions(obj: AudioFlow): List<ContextualizedAction> {
        val extraActions = when (obj) {
            is SynthFlow -> synthFlowActions.withContext(obj)
            else -> emptyList()
        }
        return extraActions + defaultActions.withContext(obj)
    }

    override fun deleteObject(obj: AudioFlow) {
        flows.removeFlow(obj)
    }

    override fun addObject(obj: AudioFlow, idx: Int) {
        flows.addFlow(obj)
    }

    override fun dataFormat(obj: AudioFlow): DataFormat? = AudioFlow.DATA_FORMAT

    private fun onDrop(ev: DragEvent) {
        val reference = ev.dragboard.getContent(AudioFlow.DATA_FORMAT) as AudioFlows.FlowReference
        val flow = reference.getFrom(flows)
        var newIndex = flowsList.children.binarySearchBy(ev.y) { n -> n.layoutY }
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
    }

    private fun makeAddFlowButton(): BorderPane {
        val btn = MaterialDesignP.PLUS.button("Add flow").styleClass("large-icon-button")
        val pane = BorderPane(btn)
        val registry = flows.context[InstrumentRegistry]
        pane.setupDropArea(condition = { db ->
            db.hasContent(SynthDefObject.DATA_FORMAT) &&
                    db.getFrom(registry, SynthDefObject.DATA_FORMAT) is SynthDefObject
        }) { ev ->
            val def = ev.dragboard.getFrom(registry, SynthDefObject.DATA_FORMAT) as SynthDefObject
            val flow = SynthFlow.createFor(associatedBus, def, flows.context)
            flows.addFlow(flow)
        }
        btn.setOnMouseClicked {
            val options = FlowOption.getOptions(flows.context)
            SimpleSearchableListView(options, "Add flow").showPopup(anchorNode = btn) { option ->
                option.createFlow(flows.context, btn, associatedBus) { flow ->
                    flow.initialize(flows.context, associatedBus)
                    flows.addFlow(flow)
                }
            }
        }
        return pane
    }

    fun addedFlow(flow: AudioFlow, index: Int) {
        flowsList.add(index, flow)
    }

    fun removedFlow(flow: AudioFlow) {
        flowsList.remove(flow)
    }

    fun movedFlow(oldIndex: Int, newIndex: Int) {
        val flow = flows.associatedFlows(associatedBus)[oldIndex]
        flowsList.remove(flow)
        flowsList.add(newIndex, flow)
    }

    companion object {
        private val defaultActions = collectActions<AudioFlow> {
            addAction("Move up") {
                applicableIf { flow -> flow.isFirst.not() }
                ifNotApplicable(Action.IfNotApplicable.Disable)
                shortcut("Ctrl+UP")
                icon(Material2AL.ARROW_UPWARD)
                executes { flow ->
                    val flows = flow.context[currentProject].flows
                    val index = flows.indexOf(flow)
                    flows.moveFlow(flow, index - 1)
                }
            }
            addAction("Move down") {
                applicableIf { flow -> flow.isLast.not() }
                ifNotApplicable(Action.IfNotApplicable.Disable)
                shortcut("Ctrl+DOWN")
                icon(Material2AL.ARROW_DOWNWARD)
                executes { flow ->
                    val flows = flow.context[currentProject].flows
                    val index = flows.indexOf(flow)
                    flows.moveFlow(flow, index + 1)
                }
            }
            addAction("Toggle activated") {
                icon { flow ->
                    flow.isActive.map { active ->
                        if (active) MaterialDesignR.RADIOBOX_MARKED
                        else MaterialDesignR.RADIOBOX_BLANK
                    }
                }
                applicableIf { flow -> reactiveValue(flow.canDeactivate) }
                shortcut("Ctrl+T")
                executes { flow -> flow.isActive.now = !flow.isActive.now }
            }
            addAction("Remove flow") {
                icon(MaterialDesignC.CLOSE_BOX)
                shortcuts("Ctrl+DELETE")
                executes { flow ->
                    val flows = flow.context[currentProject].flows
                    flows.removeFlow(flow)
                }
            }
        }


        private val synthFlowActions = collectActions<SynthFlow> {
            addAction("View SynthDef") {
                icon(Material2AL.CODE)
                shortcut("Ctrl+L")
                applicableIf { flow -> flow.synthDefSelector.isResolved }
                ifNotApplicable(Action.IfNotApplicable.Disable)
                executes { flow ->
                    val instrumentsPane = flow.context[XenakisMainActivity].instrumentsPane
                    instrumentsPane.editInstrument(flow.synthDef)
                }
            }
            addAction("Add parameter") {
                icon(Material2MZ.PLUS)
                shortcut("Ctrl+INSERT")
                executes { flow, ev ->
                    if (ev.isShiftDown()) {
                        flow.addControlsForAllObjectParameters()
                    } else {
                        ParameterizedScoreObjectView.addNewControl(flow, ev!!.target as Region)
                    }
                }
            }
        }

    }
}