package xenakis.ui.flow

import fxutils.setBackground
import javafx.scene.control.ScrollPane
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import xenakis.model.flow.AudioFlow
import xenakis.model.flow.AudioFlows
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectRegistry

class FlowPane(
    private val flows: AudioFlows
) : ScrollPane(), AudioFlows.Listener, ObjectRegistry.Listener<BusObject> {
    private val hbox = HBox(5.0)
    private val boxes = mutableMapOf<BusObject, VerticalFlowsBox>()
    private val buses = flows.context[BusRegistry]

    init {
        styleClass.add("flow-pane")
        hbox.setBackground(Color.BLACK)
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
        val box = VerticalFlowsBox(flows, obj)
        boxes[obj] = box
        hbox.children.add(box)
        box.prefHeightProperty().bind(heightProperty())
    }

    override fun removed(obj: BusObject, idx: Int) {
        val box = boxes.remove(obj) ?: error("No box found for $obj")
        hbox.children.remove(box)
    }
}