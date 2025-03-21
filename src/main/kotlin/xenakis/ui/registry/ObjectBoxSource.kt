package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import xenakis.model.registry.NamedObject

interface ObjectBoxSource<O : NamedObject> {
    val items: List<O>

    val enableReordering: Boolean get() = false

    val orientation: Orientation get() = Orientation.HORIZONTAL

    val buttonStyle: String get() = "medium-icon-button"

    fun getContent(obj: O): List<Node> = emptyList()

    fun getActions(obj: O): List<ContextualizedAction> = emptyList()

    fun removeObject(obj: O) {
        throw UnsupportedOperationException()
    }

    fun addObject(obj: O, idx: Int = items.size) {
        throw UnsupportedOperationException()
    }

    fun moveObject(obj: O, idx: Int) {
        removeObject(obj)
        addObject(obj, idx)
    }

    fun configureDragboard(obj: O, dragboard: Dragboard) {}

    fun dataFormat(obj: O): DataFormat? = null

    fun onSelected(obj: O) {}
}