package xenakis.ui

import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.input.TransferMode
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asProperty
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.model.BusObject
import xenakis.model.BusRegistry
import xenakis.sc.Rate

class BusRegistryPane(private val busses: BusRegistry) : ObjectRegistryPane<BusObject>(busses) {
    init {
        busses.addView(this)
    }

    override fun reload() {
        busses.run { context[SuperColliderClient].reallocateBusses() }
    }

    override fun addObject(name: String) {
        val bus = BusObject.create(name)
        busses.add(bus)
    }

    override fun canDelete(obj: BusObject): Boolean = !obj.isOutput

    override fun ObjectBox<BusObject>.configureObjectBox() {
        val rateSelector = ComboBox(FXCollections.observableList(Rate.values().asList()))
        if (obj.rate is ReactiveVariable) {
            rateSelector.valueProperty().bindBidirectional(obj.rate.asProperty())
        } else {
            rateSelector.valueProperty().bind(obj.rate.asObservableValue())
            rateSelector.isEditable = false
        }
        addExtraControl(rateSelector)
        val channelsSpinner = Spinner<Int>(0, 12, 2)
        channelsSpinner.prefWidth = 70.0
        if (obj.channels is ReactiveVariable) {
            channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        } else {
            channelsSpinner.valueFactory.valueProperty().bind(obj.channels.asObservableValue())
            channelsSpinner.isEditable = false
        }
        addExtraControl(channelsSpinner)
        setOnDragDetected { ev ->
            val db = startDragAndDrop(TransferMode.LINK)
            db.setContent(mapOf(BusObject.DATA_FORMAT to obj.name.now))
            ev.consume()
        }
    }
}