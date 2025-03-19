package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.setFixedWidth
import hextant.context.createControl
import hextant.context.withoutUndo
import javafx.scene.Node
import javafx.scene.control.Spinner
import javafx.scene.paint.Color
import org.kordamp.ikonli.evaicons.Evaicons
import reaktive.value.forEach
import reaktive.value.fx.asProperty
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.toDecimal
import xenakis.impl.zero
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.DecimalLiteralEditor

class ControlBusRegistryPane(private val busses: BusRegistry) : SuperColliderObjectRegistryPane<BusObject>(busses) {
    init {
        busses.addListener(this, initialize = false)
    }

    override fun addObject(name: String): BusObject {
        val spec = NumericalControlSpec(0.0, 0.0, 1.0, 0.01.toDecimal(), Warp.Linear)
        val bus = BusObject.control(name, 1, spec)
        busses.add(bus)
        return bus
    }

    override fun filter(obj: BusObject): Boolean = obj is BusObject.ControlBus

    override fun getContent(obj: BusObject): List<Node> {
        if (obj !is BusObject.ControlBus) return emptyList()
        val channelsSpinner = Spinner<Int>(1, 12, 2).setFixedWidth(60.0)
        channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        val defaultValue = DecimalLiteralEditor(obj.spec.now.defaultValue.text) //TODO replace with ControlSlider
        defaultValue.initialize(obj.context)
        val defaultValueCtrl = obj.context.createControl(defaultValue)
        defaultValueCtrl.userData = obj.spec.forEach { spec ->
            val t = spec.defaultValue.text
            if (defaultValue.text.now != t) {
                defaultValue.setText(t)
            }
        } and defaultValue.result.observe { _, _, newDefault ->
            val value = newDefault.get()
            if (value != obj.defaultValue.now) {
                obj.spec.now = NumericalControlSpec(value, value, value, zero, Warp.Linear, Color.GRAY)
            }
        }
        return listOf(channelsSpinner, defaultValueCtrl)
    }

    override fun getActions(obj: BusObject): List<ContextualizedAction> = actions.withContext(obj)

    companion object {
        private val actions = collectActions<BusObject> {
            addAction("Monitor bus") {
                icon(Evaicons.ACTIVITY)
                shortcut("Ctrl+M")
                executes { bus -> bus.context[SuperColliderClient].run("${bus.superColliderName}.scope;") }
            }
        }
    }
}