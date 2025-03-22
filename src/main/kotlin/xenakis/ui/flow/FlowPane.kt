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
import xenakis.model.flow.AudioFlows
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.NamedObjectList
import xenakis.ui.controls.NamePrompt
import xenakis.ui.registry.ToolPane

class FlowPane(
    private val flows: AudioFlows
) : ToolPane(), NamedObjectList.Listener<BusObject> {
    private val hbox = HBox(4.0)
    private val verticalBoxes = mutableMapOf<BusObject, FlowChainView>()
    private val buses = flows.context[BusRegistry]

    init {
        styleClass.add("flow-pane")
        setBackground(Color.BLACK)
        val scrollPane = ScrollPane(hbox).apply {
            vbarPolicy = ScrollBarPolicy.NEVER
            isFitToHeight = true
        }
        setup("Audio Flows", content = HBox(scrollPane, makeAddBusButton()))
        buses.addListener(this)
    }

    private fun makeAddBusButton(): BorderPane {
        val btn = MaterialDesignP.PLUS.button("Add flow") { ev ->
            val name = NamePrompt(flows.context[BusRegistry], "Name for new bus", "").showDialog(ev)
                ?: return@button
            val bus = BusObject.audio(name)
            buses.add(bus)
        }.styleClass("large-icon-button")
        val pane = BorderPane(btn)
        HBox.setHgrow(pane, Priority.ALWAYS)
        return pane
    }

    override fun added(obj: BusObject, idx: Int) {
        if (obj !is BusObject.AudioBus) return
        val box = FlowChainView(flows, obj)
        verticalBoxes[obj] = box
        hbox.children.add(box)
        box.prefHeightProperty().bind(heightProperty())
    }

    override fun removed(obj: BusObject) {
        if (obj !is BusObject.AudioBus) return
        val box = verticalBoxes.remove(obj) ?: error("No box found for $obj")
        hbox.children.remove(box)
    }
}