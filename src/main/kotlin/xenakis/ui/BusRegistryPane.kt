package xenakis.ui

import hextant.context.Context
import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asProperty
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderClient
import xenakis.model.BusObject
import xenakis.model.BusRegistry
import xenakis.sc.Identifier
import xenakis.sc.Rate

class BusRegistryPane(private val context: Context, private val busses: BusRegistry) : BusRegistry.View, VBox() {
    init {
        styleClass("tool-pane")
        children.add(createHeader())
        busses.addView(this)
    }

    private fun createHeader(): HBox {
        val label = Label("Busses").styleClass("tool-pane-heading")
        val space = infiniteSpace()
        val addBtn = Icon.Add.button(action = "Add bus") { createNewBus() }
        val reloadBtn = Icon.Repeat.button(action = "Sync Busses") {
            val client = context[SuperColliderClient]
            busses.run { client.reallocateBusses() }
        }
        return HBox(label, space, addBtn, reloadBtn).styleClass("tool-pane-header")
    }

    override fun added(bus: BusObject, idx: Int) {
        val rateSelector = ComboBox(FXCollections.observableList(Rate.values().asList()))
        if (bus.rate is ReactiveVariable) {
            rateSelector.valueProperty().bindBidirectional(bus.rate.asProperty())
        } else {
            rateSelector.valueProperty().bind(bus.rate.asObservableValue())
            rateSelector.isEditable = false
        }
        val channelsSpinner = Spinner<Int>(0, 12, 2)
        channelsSpinner.prefWidth = 70.0
        if (bus.channels is ReactiveVariable) {
            channelsSpinner.valueFactory.valueProperty().bindBidirectional(bus.channels.asProperty())
        } else {
            channelsSpinner.valueFactory.valueProperty().bind(bus.channels.asObservableValue())
            channelsSpinner.isEditable = false
        }
        val removeBtn = Icon.Delete.button(action = "Remove bus") { busses.remove(bus) }
        val box = HBox(
            NameControl(bus), rateSelector, channelsSpinner,
            infiniteSpace()
        ) styleClass "bus-box"
        if (!bus.isOutput) box.children.add(removeBtn)
        box.setOnDragDetected { ev ->
            val db = box.startDragAndDrop(TransferMode.LINK)
            db.setContent(mapOf(BusObject.DATA_FORMAT to bus.name.now))
            ev.consume()

        }
        children.add(idx + 1, box)
    }

    override fun removed(bus: BusObject, idx: Int) {
        children.removeAt(idx + 1)
    }

    fun createNewBus(rate: Rate = Rate.Audio, channels: Int = 2, confirmed: (BusObject) -> Unit = {}) {
        showTextPrompt("Bus name", "", context) { name ->
            if (!Identifier.isValid(name)) return@showTextPrompt false
            if (busses.hasBus(name)) return@showTextPrompt false
            val bus = BusObject(reactiveVariable(name), reactiveVariable(rate), reactiveVariable(channels))
            busses.add(bus)
            confirmed(bus)
            true
        }
    }
}