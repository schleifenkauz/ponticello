package ponticello.ui.dock

import hextant.context.Context
import ponticello.model.registry.ObjectList

class ToolPaneTypeList(override val objects: MutableList<ToolPane.Type>) : ObjectList<ToolPane.Type>() {
    override val objectType: String
        get() = "ToolPane"

    companion object {
        fun new(context: Context) = ToolPaneTypeList(mutableListOf()).also { it.initialize(context) }
    }
}