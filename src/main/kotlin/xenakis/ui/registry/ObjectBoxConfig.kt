package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue
import xenakis.model.registry.NamedObject
import xenakis.model.registry.NamedObject.Companion.NO_NAME

interface ObjectBoxConfig<O : NamedObject> {
    val enableReordering: Boolean get() = false

    val orientation: Orientation get() = Orientation.HORIZONTAL

    val buttonStyle: String get() = "medium-icon-button"

    fun getContent(obj: O): List<Node> = emptyList()

    fun getActions(obj: O): List<ContextualizedAction> = emptyList()

    fun configureDragboard(obj: O, dragboard: Dragboard) {}

    fun dataFormat(obj: O): DataFormat? = null

    fun getDefaultDisplayName(obj: O): ReactiveString = reactiveValue(NO_NAME)

    fun onSelected(obj: O) {}

    fun onRemoved(obj: O) {}
}