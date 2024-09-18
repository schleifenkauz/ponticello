package xenakis.ui

import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.input.TransferMode
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asProperty
import xenakis.model.BusObject
import xenakis.model.BusRegistry
import xenakis.sc.Rate

class BusRegistryPane(private val busses: BusRegistry) : SuperColliderObjectRegistryPane<BusObject>(busses) {
    init {
        busses.addListener(this)
    }

    override fun addObject(name: String): BusObject {
        val bus = BusObject.create(name)
        busses.add(bus)
        return bus
    }

    override fun canDelete(obj: BusObject): Boolean = !obj.isOutput

    override fun ObjectBox<BusObject>.configureObjectBox() {
        val rateSelector = ComboBox(FXCollections.observableList(Rate.values().asList()))
        rateSelector.minWidth = USE_PREF_SIZE
        val channelsSpinner = Spinner<Int>(0, 12, 2).setFixedWidth(50.0)
        if (obj.isOutput) {
            rateSelector.valueProperty().bind(obj.rate.asObservableValue())
            rateSelector.isDisable = true
            channelsSpinner.valueFactory.valueProperty().bind(obj.channels.asObservableValue())
            channelsSpinner.isDisable = true
        } else {
            rateSelector.valueProperty().bindBidirectional(obj.rate.asProperty())
            channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        }
        addGrabber(BusObject.DATA_FORMAT, transferMode = TransferMode.LINK)
        addExtraControl(rateSelector, channelsSpinner)
    }
}