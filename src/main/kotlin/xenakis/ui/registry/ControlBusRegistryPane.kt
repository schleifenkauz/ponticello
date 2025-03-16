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
import xenakis.impl.zero
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.DecimalLiteralEditor

class ControlBusRegistryPane(private val busses: BusRegistry) : SuperColliderObjectRegistryPane<BusObject>(busses) {
    init {
        busses.addListener(this)
    }

    override fun addObject(name: String): BusObject {
        val bus = BusObject.audio(name)
        busses.add(bus)
        return bus
    }

    override fun filter(obj: BusObject): Boolean = obj is BusObject.ControlBus

    override fun getContent(obj: BusObject): List<Node> {
        obj as BusObject.ControlBus
        val channelsSpinner = Spinner<Int>(0, 12, 2).setFixedWidth(50.0)
        channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        val defaultValue = DecimalLiteralEditor() //TODO replace with ControlSlider
        obj.context.withoutUndo { defaultValue.setText(obj.spec.now.defaultValue.text) }
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