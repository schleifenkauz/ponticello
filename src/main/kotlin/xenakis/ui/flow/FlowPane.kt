package xenakis.ui.flow

import fxutils.actions.button
import fxutils.setBackground
import fxutils.styleClass
import javafx.scene.control.ScrollPane
import javafx.scene.control.ScrollPane.ScrollBarPolicy
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.paint.Color
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import xenakis.model.flow.AudioFlow
import xenakis.model.flow.AudioFlows
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.controls.NamePrompt

class FlowPane(
    private val flows: AudioFlows
) : HBox(), AudioFlows.Listener, ObjectRegistry.Listener<BusObject> {
    private val hbox = HBox(4.0)
    private val verticalBoxes = mutableMapOf<BusObject, VerticalFlowsBox>()
    private val buses = flows.context[BusRegistry]

    init {
        styleClass.add("flow-pane")
        setBackground(Color.BLACK)
        val scrollPane = ScrollPane(hbox).apply {
            vbarPolicy = ScrollBarPolicy.NEVER
            isFitToHeight = true
        }
        children.addAll(scrollPane, makeAddBusButton())
        buses.addListener(this)
        flows.addListener(this)
    }

    private fun makeAddBusButton(): BorderPane {
        val btn = MaterialDesignP.PLUS.button("Add flow") { ev ->
            val name = NamePrompt(flows.context[BusRegistry], "Name for new bus", "").showDialog(ev)
                ?: return@button
            val bus = BusObject.create(name)
            buses.add(bus)
        }.styleClass("large-icon-button")
        val pane = BorderPane(btn)
        setHgrow(pane, Priority.ALWAYS)
        return pane
    }

    override fun addedFlow(flow: AudioFlow, index: Int) {
        val box = verticalBoxes.getValue(flow.associatedBus)
        box.addedFlow(flow, index)
    }

    override fun removedFlow(flow: AudioFlow) {
        val box = verticalBoxes.getValue(flow.associatedBus)
        box.removedFlow(flow)
    }

    override fun movedFlow(flow: AudioFlow, oldIndex: Int, newIndex: Int) {
        verticalBoxes.getValue(flow.associatedBus).movedFlow(oldIndex, newIndex)
    }

    override fun added(obj: BusObject, idx: Int) {
        val box = VerticalFlowsBox(flows, obj)
        verticalBoxes[obj] = box
        hbox.children.add(box)
        box.prefHeightProperty().bind(heightProperty())
    }

    override fun removed(obj: BusObject, idx: Int) {
        val box = verticalBoxes.remove(obj) ?: error("No box found for $obj")
        hbox.children.remove(box)
    }
}