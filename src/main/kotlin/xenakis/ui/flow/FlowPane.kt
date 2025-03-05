package xenakis.ui.flow

import bundles.createBundle
import hextant.fx.setBackground
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.control.Slider
import javafx.scene.input.DragEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.ReactiveString
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.binding.notEqualTo
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.toDecimal
import xenakis.model.flow.*
import xenakis.model.obj.BusObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import xenakis.model.score.KnobControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.editor.BusSelector
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.actions.ActionBar
import xenakis.ui.actions.button
import xenakis.ui.actions.collectActions
import xenakis.ui.actions.registerShortcuts
import xenakis.ui.controls.DetailPane
import xenakis.ui.controls.Knob
import xenakis.ui.impl.*
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.registry.SimpleSearchableListView
import xenakis.ui.score.ParameterizedScoreObjectView

class FlowPane(
    private val flows: AudioFlows
) : ScrollPane(), AudioFlows.Listener, ObjectRegistry.Listener<BusObject> {
    private val hbox = HBox(5.0)
    private val boxes = mutableMapOf<BusObject, BusBox>()
    private val buses = flows.context[BusRegistry]

    init {
        styleClass.add("flow-pane")
        hbox.setBackground(Color.web("#1d1d1e"))
        vbarPolicy = ScrollBarPolicy.NEVER
        content = hbox
        buses.addListener(this)
        flows.addListener(this)
    }

    override fun addedFlow(flow: AudioFlow) {
        val box = boxes.getValue(flow.associatedBus)
        box.addedFlow(flow)
    }

    override fun removedFlow(flow: AudioFlow) {
        val box = boxes.getValue(flow.associatedBus)
        box.removedFlow(flow)
    }

    override fun movedFlow(flow: AudioFlow, oldIndex: Int) {
        boxes.getValue(flow.associatedBus).movedFlow(flow, oldIndex)
    }

    override fun added(obj: BusObject, idx: Int) {
        val box = BusBox(flows, obj)
        boxes[obj] = box
        hbox.children.add(box)
        box.prefHeightProperty().bind(heightProperty())
    }

    override fun removed(obj: BusObject, idx: Int) {
        val box = boxes.remove(obj) ?: error("No box found for $obj")
        hbox.children.remove(box)
    }

    private class BusBox(
        private val flows: AudioFlows,
        private val bus: BusObject
    ) : ScrollPane() {
        val vbox = VBox(5.0)
        private val label = label(bus.name) styleClass "bus-label"
        private val boxes = mutableMapOf<AudioFlow, FlowBox<*>>()

        init {
            styleClass.add("bus-box")
            val addFlowRegion = makeAddFlowButton()
            val layout = VBox(vbox, addFlowRegion, BorderPane(label) styleClass "bus-label-box")
            VBox.setVgrow(addFlowRegion, Priority.ALWAYS)
            layout.setBackground(Color.BLACK)
            layout.minHeightProperty().bind(viewportBoundsProperty().map(Bounds::getHeight))
            setupDropArea({ db -> db.hasContent(AudioFlow.DATA_FORMAT) }, ::onDrop)
            content = layout
        }

        private fun onDrop(ev: DragEvent) {

        }

        private fun makeAddFlowButton(): BorderPane {
            val btn = MaterialDesignP.PLUS.button("Add flow").styleClass("tool-button")
            val pane = BorderPane(btn)
            pane.setupDropArea({ db -> db.hasContent(SynthDefObject.DATA_FORMAT) }) { ev ->
                val registry = flows.context[InstrumentRegistry]
                val def = ObjectReference(ev.dragboard.getFrom(registry, SynthDefObject.DATA_FORMAT) as SynthDefObject)
                val flow = SynthFlow.createFor(bus, def.get(), flows.context)
                flow.index.now = vbox.children.size
                flows.addFlow(flow)
            }
            btn.setOnMouseClicked {
                val options = FlowOption.getOptions(flows.context)
                SimpleSearchableListView(options, "Add flow").showPopup(flows.context, anchorNode = btn) { option ->
                    option.createFlow(flows.context, btn, bus) { flow ->
                        flow.index.now = vbox.children.size
                        flows.addFlow(flow)
                    }
                }
            }
            return pane
        }

        fun addedFlow(flow: AudioFlow) {
            val box = when (flow) {
                is CodeFlow -> CodeFlowBox(flow)
                is SynthFlow -> SynthFlowBox(flow)
                is UtilityFlow -> UtilityFlowBox(flow)
                is SendFlow -> SendUtilityBox(flow)
                is ScoreObjectPlaceholder -> PlaceholderBox(flow)
            }
            box.initialize(this)
            boxes[flow] = box
            vbox.children.add(flow.index.now, box)
        }

        fun removedFlow(flow: AudioFlow) {
            val box = boxes.remove(flow)
            vbox.children.remove(box)
        }

        fun movedFlow(flow: AudioFlow, oldIndex: Int) {
            val box = vbox.children.removeAt(oldIndex)
            vbox.children.add(flow.index.now, box)
        }
    }

    private abstract class FlowBox<F : AudioFlow>(val flow: F) : VBox() {
        abstract fun getContent(flow: F): Node

        abstract fun getTitle(flow: F): ReactiveString

        init {
            styleClass.add("flow-box")
            setOnMouseClicked { requestFocus() }
            registerShortcuts(actions.withContext(flow))
            /*grabber.setOnDragDetected { ev ->
                val mode = if (ev.isControlDown) TransferMode.COPY else TransferMode.MOVE
                val db = startDragAndDrop(mode)
                val referenceIndex = flow.context[currentProject].flows.referenceIndex(flow)
                db.setContent(mapOf(AudioFlow.DATA_FORMAT to referenceIndex))
                ev.consume()
            }*/
        }

        fun initialize(parent: BusBox) {
            val actionBar = ActionBar(actions.withContext(flow), border = false, buttonStyle = "flow-action-button")
            val header = HBox(
                label(getTitle(flow)),
                infiniteSpace(),
                actionBar
            ) styleClass "flow-box-header"
            val grabber = actionBar.getButton(actions.getAction("Drag flow"))
            grabber.setupDragging(flow.context, null,
                onPressed = { viewOrder = -100.0 },
                relocateBy = { _, _, _, _, dy -> translateY = dy },
                onReleased = {
                    viewOrder = 0.0
                    try {
                        val y = translateY + layoutY
                        var newIndex = parent.vbox.children.binarySearchBy(y) { n -> n.layoutY }
                        if (newIndex < 0) newIndex = -(newIndex + 1)
                        flow.context[currentProject].flows.moveFlow(flow, newIndex)
                    } finally {
                        translateY = 0.0
                    }
                }
            )
            children.addAll(header, getContent(flow))
        }

        companion object {
            private val actions = collectActions<AudioFlow> {
                addAction("Move up") {
                    applicableIf { flow -> flow.index.notEqualTo(0) }
                    shortcut("Ctrl+UP")
                    icon(Material2AL.ARROW_UPWARD)
                    executes { flow ->
                        val flows = flow.context[currentProject].flows
                        flows.moveFlow(flow, flow.index.now - 1)
                    }
                }
                addAction("Move down") {
                    applicableIf { flow ->
                        val flows = flow.context[currentProject].flows
                        val lastFlowIndex = flows.numberOfFlows(flow.associatedBus).map { s -> s - 1 }
                        flow.index.notEqualTo(lastFlowIndex)
                    }
                    shortcut("Ctrl+DOWN")
                    icon(Material2AL.ARROW_DOWNWARD)
                    executes { flow ->
                        val flows = flow.context[currentProject]
                        flows.context[currentProject].flows.moveFlow(flow, flow.index.now + 1)
                    }
                }
                addAction("Toggle activated") {
                    icon { flow ->
                        flow.isActive.map { active ->
                            if (active) MaterialDesignR.RADIOBOX_MARKED
                            else MaterialDesignR.RADIOBOX_BLANK
                        }
                    }
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
                addAction("Drag flow") {
                    icon(MaterialDesignC.CURSOR_POINTER)
                }
            }
        }
    }

    private class CodeFlowBox(flow: CodeFlow) : FlowBox<CodeFlow>(flow) {
        override fun getContent(flow: CodeFlow): Node = flow.codeEditor.control

        override fun getTitle(flow: CodeFlow): ReactiveString = reactiveValue("Code")
    }

    private class SynthFlowBox(flow: SynthFlow) : FlowBox<SynthFlow>(flow) {
        override fun getContent(flow: SynthFlow): Node {
            val detailPane = DetailPane()
            ParameterizedScoreObjectView.setupSynthDetailPane(detailPane, flow)
            return detailPane
        }

        override fun getTitle(flow: SynthFlow): ReactiveString = flow.synthDef.name
    }

    private class UtilityFlowBox(flow: UtilityFlow) : FlowBox<UtilityFlow>(flow) {
        override fun getContent(flow: UtilityFlow): Node {
            val slider = Slider(-61.0, +6.0, 0.0) styleClass "volume-fader"
            return HBox(slider)
        }

        override fun getTitle(flow: UtilityFlow): ReactiveString = reactiveValue("Utility")
    }

    private class SendUtilityBox(flow: SendFlow) : FlowBox<SendFlow>(flow) {
        override fun getContent(flow: SendFlow): Node {
            val ctrl = KnobControl(flow.amountPercent)
            val spec = NumericalControlSpec(100.0, 0.0, 100.0, 1.toDecimal(), Warp.Linear)
            val knob = Knob("Amount", ctrl, spec, flow.context)
            val targetBusSelector = BusSelector(
                flow.context,
                preferredRate = flow.associatedBus.rate.now,
                preferredChannels = flow.associatedBus.channels.now,
                flow.targetRef as ReactiveVariable<ObjectReference?>
            )
            val selectorControl = ObjectSelectorControl(targetBusSelector, createBundle())
            return HBox(10.0, knob, selectorControl).centerChildren()
        }

        override fun getTitle(flow: SendFlow): ReactiveString = reactiveValue("Send")
    }

    private class PlaceholderBox(placeholder: ScoreObjectPlaceholder) : FlowBox<ScoreObjectPlaceholder>(placeholder) {
        override fun getContent(flow: ScoreObjectPlaceholder): Node = label(flow.group.name)

        override fun getTitle(flow: ScoreObjectPlaceholder): ReactiveString = reactiveValue("Placeholder")
    }
}