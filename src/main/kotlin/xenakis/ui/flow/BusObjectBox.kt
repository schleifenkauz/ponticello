package xenakis.ui.flow

import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.infiniteSpace
import fxutils.setFixedWidth
import fxutils.styleClass
import javafx.scene.control.Spinner
import javafx.scene.layout.HBox
import org.kordamp.ikonli.evaicons.Evaicons
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asProperty
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.actions.RegistryObjectActions
import xenakis.ui.controls.NameControl

class BusObjectBox(obj: BusObject) : HBox(5.0) {
    private val channelsSpinner = Spinner<Int>(0, 128, 2).setFixedWidth(65.0)

    init {
        styleClass("bus-box")
        if (obj.busType != BusObject.Type.Regular) {
            channelsSpinner.valueFactory.valueProperty().bind(obj.channels.asObservableValue())
            channelsSpinner.isDisable = true
        } else {
            channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        }
        children.addAll(
            NameControl(obj),
            channelsSpinner, infiniteSpace(),
            ActionBar(actions.withContext(obj), buttonStyle = "medium-icon-button")
        )
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
            add(RegistryObjectActions.deleteAction(BusRegistry))
        }
    }
}