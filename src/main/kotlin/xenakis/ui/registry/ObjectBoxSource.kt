package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import javafx.scene.Node
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import xenakis.model.registry.NamedObject

interface ObjectBoxSource<O : NamedObject> {
    val items: List<O>

    fun getContent(obj: O): List<Node> = emptyList()

    fun getActions(obj: O): List<ContextualizedAction> = emptyList()

    fun deleteObject(obj: O) {
        throw UnsupportedOperationException()
    }

    fun addObject(obj: O) {
        throw UnsupportedOperationException()
    }

    fun configureDragboard(obj: O, dragboard: Dragboard) {}

    fun dataFormat(obj: O): DataFormat? = null

    val enableReordering: Boolean get() = false
}