package xenakis.ui.registry

import fxutils.setFixedWidth
import hextant.context.createControl
import hextant.context.withoutUndo
import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.input.TransferMode
import org.kordamp.ikonli.evaicons.Evaicons
import reaktive.value.binding.equalTo
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asProperty
import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.sc.Rate
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.DecimalLiteralEditor

class BusRegistryPane(private val busses: BusRegistry) : SuperColliderObjectRegistryPane<BusObject>(busses) {
    init {
        busses.addListener(this)
    }

    override fun addObject(name: String): BusObject {
        val bus = BusObject.create(name)
        busses.add(bus)
        return bus
    }

    override fun canDelete(obj: BusObject): Boolean = obj.type == BusObject.Type.Regular

    override fun ObjectBox<BusObject>.configureObjectBox() {
        val rateSelector = ComboBox(FXCollections.observableList(Rate.entries))
        rateSelector.minWidth = USE_PREF_SIZE
        val channelsSpinner = Spinner<Int>(0, 12, 2).setFixedWidth(50.0)
        if (obj.type != BusObject.Type.Regular) {
            rateSelector.valueProperty().bind(obj.rate.asObservableValue())
            rateSelector.isDisable = true
            channelsSpinner.valueFactory.valueProperty().bind(obj.channels.asObservableValue())
            channelsSpinner.isDisable = true
        } else {
            rateSelector.valueProperty().bindBidirectional(obj.rate.asProperty())
            channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        }
        val defaultValue = DecimalLiteralEditor(obj.context)
        obj.context.withoutUndo { defaultValue.setText(obj.defaultValue.now?.text ?: "0") }
        val defaultValueCtrl = obj.context.createControl(defaultValue)
        defaultValueCtrl.userData = obj.defaultValue.forEach { v ->
            val t = v?.text ?: "0"
            if (defaultValue.text.now != t) {
                defaultValue.setText(t)
            }
        } and defaultValue.result.observe { _, _, newDefault ->
            if (newDefault != obj.defaultValue.now) obj.defaultValue.now = newDefault
        }
        defaultValueCtrl.visibleProperty().bind(obj.rate.equalTo(Rate.Control).asObservableValue())
        addGrabber(BusObject.DATA_FORMAT, transferMode = TransferMode.LINK)
        addAction(Evaicons.ACTIVITY, "Monitor bus") {
            registry.context[SuperColliderClient].run("${obj.superColliderName}.scope;")
        }
        addExtraControl(defaultValueCtrl, rateSelector, channelsSpinner)
    }
}