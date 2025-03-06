package xenakis.ui.flow

import fxutils.actions.button
import fxutils.prompt.SimpleSearchableListView
import fxutils.setBackground
import fxutils.setupDropArea
import fxutils.styleClass
import javafx.geometry.Bounds
import javafx.scene.control.ScrollPane
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import reaktive.value.now
import xenakis.model.flow.*
import xenakis.model.obj.BusObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ObjectReference
import xenakis.ui.impl.getFrom
import xenakis.ui.impl.label
import xenakis.ui.launcher.XenakisLauncher

class VerticalFlowsBox(
    private val flows: AudioFlows,
    private val associatedBus: BusObject
) : ScrollPane() {
    val vbox = VBox(5.0)
    private val label = label(associatedBus.name) styleClass "bus-label"
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
        val referenceIndex = ev.dragboard.getContent(AudioFlow.DATA_FORMAT) as Int
        val flow = flows.getFlow(referenceIndex)
        var newIndex = vbox.children.binarySearchBy(ev.y) { n -> n.layoutY }
        if (newIndex < 0) newIndex = -(newIndex + 1)
        if (flow.associatedBus != associatedBus) {
            val copy = flow.copyFor(associatedBus)
            copy.index.now = newIndex
            flows.addFlow(copy)
            if (TransferMode.COPY !in ev.dragboard.transferModes) {
                flows.removeFlow(flow)
            }
        } else {
            if (TransferMode.MOVE !in ev.dragboard.transferModes) return
            flow.context[XenakisLauncher.currentProject].flows.moveFlow(flow, newIndex)
        }
    }

    private fun makeAddFlowButton(): BorderPane {
        val btn = MaterialDesignP.PLUS.button("Add flow").styleClass("large-icon-button")
        val pane = BorderPane(btn)
        pane.setupDropArea({ db -> db.hasContent(SynthDefObject.DATA_FORMAT) }) { ev ->
            val registry = flows.context[InstrumentRegistry]
            val def = ObjectReference(ev.dragboard.getFrom(registry, SynthDefObject.DATA_FORMAT) as SynthDefObject)
            val flow = SynthFlow.createFor(associatedBus, def.get(), flows.context)
            flow.index.now = vbox.children.size
            flows.addFlow(flow)
        }
        btn.setOnMouseClicked {
            val options = FlowOption.getOptions(flows.context)
            SimpleSearchableListView(options, "Add flow").showPopup(anchorNode = btn) { option ->
                option.createFlow(flows.context, btn, associatedBus) { flow ->
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
        box.initialize()
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