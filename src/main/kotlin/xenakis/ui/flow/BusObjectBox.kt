package xenakis.ui.flow

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.prompt.SimpleSearchableListView
import hextant.context.createControl
import hextant.context.withoutUndo
import hextant.core.view.EditorControl
import javafx.scene.control.Spinner
import javafx.scene.layout.HBox
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
import xenakis.ui.actions.RegistryObjectActions

class BusObjectBox(private val obj: BusObject) : HBox() {
    private val label = label(obj.name) styleClass "bus-label"
    private val rateSelector = SimpleSearchableListView(Rate.entries, "Rate").selectorButton(obj.rate)
    private val channelsSpinner = Spinner<Int>(0, 128, 2).setFixedWidth(50.0)

    init {
        styleClass("bus-box")
        if (obj.type != BusObject.Type.Regular) {
            rateSelector.isDisable = true
            channelsSpinner.valueFactory.valueProperty().bind(obj.channels.asObservableValue())
            channelsSpinner.isDisable = true
        } else {
            channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        }
        val defaultValueCtrl = setupDefaultValueEditor()
        children.addAll(
            label, hspace(20.0),
            rateSelector, channelsSpinner, defaultValueCtrl, infiniteSpace(),
            ActionBar(actions.withContext(obj), buttonStyle = "medium-icon-button")
        )
    }

    private fun setupDefaultValueEditor(): EditorControl<*> {
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
        return defaultValueCtrl
    }

    companion object {
        private val actions = collectActions<BusObject> {
            addAction("Monitor bus") {
                icon(Evaicons.ACTIVITY)
                shortcut("Ctrl+M")
                executes { bus ->
                    bus.context[SuperColliderClient].run("${bus.superColliderName}.scope;")
                }
            }
            addAll(RegistryObjectActions.all(BusRegistry))
        }
    }
}