package xenakis.ui.flow

import bundles.createBundle
import hextant.fx.setBackground
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.control.Slider
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
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.toDecimal
import xenakis.model.flow.*
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
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
import xenakis.ui.controls.DetailPane
import xenakis.ui.controls.Knob
import xenakis.ui.impl.centerChildren
import xenakis.ui.impl.infiniteSpace
import xenakis.ui.impl.label
import xenakis.ui.impl.styleClass
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
        val box = boxes[flow.associatedBus] ?: error("No box found for ${flow.associatedBus}")
        box.addedFlow(flow)
    }

    override fun removedFlow(flow: AudioFlow) {
        val box = boxes[flow.associatedBus] ?: error("No box found for ${flow.associatedBus}")
        box.removedFlow(flow)
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
        private val vbox = VBox(5.0)
        private val label = label(bus.name) styleClass "bus-label"
        private val boxes = mutableListOf<FlowBox<*>>()

        init {
            styleClass.add("bus-box")
            val layout = VBox(vbox, BorderPane(label) styleClass "bus-label-box")
            VBox.setVgrow(vbox, Priority.ALWAYS)
            vbox.setBackground(Color.BLACK)
            layout.minHeightProperty().bind(viewportBoundsProperty().map(Bounds::getHeight))
            content = layout
            addCreateFlowButton(0)
        }

        private fun addCreateFlowButton(index: Int) {
            //TODO ability to drop SynthDefs here
            val btn = MaterialDesignP.PLUS.button("Add flow").styleClass("add-flow-button")
            val pane = VBox(btn).centerChildren()
            btn.setOnMouseClicked {
                val options = FlowOption.getOptions(flows.context)
                SimpleSearchableListView(options, "Add flow").showPopup(flows.context, anchorNode = btn) { option ->
                    option.createFlow(flows.context, btn, bus) { flow ->
                        flow.index = vbox.children.indexOf(pane) / 2
                        flows.addFlow(flow)
                    }
                }
            }
            btn.visibleProperty().bind(pane.hoverProperty())
            vbox.children.add(index, pane)
        }

        fun addedFlow(flow: AudioFlow) {
            val box = when (flow) {
                is CodeFlow -> CodeFlowBox(flow)
                is SynthFlow -> SynthFlowBox(flow)
                is UtilityFlow -> UtilityFlowBox(flow)
                is SendFlow -> SendUtilityBox(flow)
                is ScoreObjectPlaceholder -> PlaceholderBox(flow)
            }
            box.initialize()
            boxes.add(flow.index, box)
            vbox.children.add(flow.index * 2 + 1, box)
            addCreateFlowButton(flow.index * 2 + 2)
        }

        fun removedFlow(flow: AudioFlow) {
            val index = boxes.indexOfFirst { b -> b.flow == flow }
            boxes.removeAt(index)
            vbox.children.removeAt(index * 2 + 2)
            vbox.children.removeAt(index * 2 + 1)
        }
    }

    private abstract class FlowBox<F : AudioFlow>(val flow: F) : VBox() {
        private val header = HBox()
        private val grabber = MaterialDesignC.CURSOR_POINTER.button("Move flow")

        abstract fun getContent(flow: F): Node

        abstract fun getTitle(flow: F): ReactiveString

        init {
            styleClass.add("flow-box")
            setOnMouseClicked { requestFocus() }
        }

        fun initialize() {
            val header = HBox(
                label(getTitle(flow)),
                infiniteSpace(),
                ActionBar(actions.withContext(flow), border = false, buttonStyle = "flow-action-button")
            ) styleClass "flow-box-header"
            children.addAll(header, getContent(flow))
        }

        companion object {
            private val actions = collectActions<AudioFlow> {
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
                    icon(Material2AL.CLOSE)
                    shortcuts("Ctrl+DELETE")
                    executes { flow ->
                        flow.context[currentProject].flows.removeFlow(flow)
                    }
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