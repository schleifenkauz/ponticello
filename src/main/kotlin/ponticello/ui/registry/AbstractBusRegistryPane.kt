package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.setFixedWidth
import javafx.scene.Node
import javafx.scene.control.Spinner
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.model.obj.BusObject
import ponticello.model.registry.BusRegistry
import ponticello.sc.client.SuperColliderClient
import reaktive.value.fx.asProperty

abstract class AbstractBusRegistryPane(busses: BusRegistry) : ObjectRegistryPane<BusObject>(busses) {
    override val enableReordering: Boolean
        get() = true

    override fun getItemContent(obj: BusObject): List<Node> {
        val channelsSpinner = Spinner<Int>(1, 12, 2).setFixedWidth(60.0)
        channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        channelsSpinner.isEditable = true
        return listOf(channelsSpinner)
    }

    override fun getActions(box: ObjectBox<BusObject>): List<ContextualizedAction> = actions.withContext(box)

    companion object {
        private val actions = collectActions<ObjectBox<BusObject>> {
            addAction("Monitor bus") {
                icon(MaterialDesignP.PULSE)
                shortcut("Ctrl+M")
                executes { box ->
                    val bus = box.obj
                    bus.context[SuperColliderClient].run("{ ${bus.superColliderName}.scope }.defer;")
                }
            }
        }
    }
}