package ponticello.ui.dock

import ponticello.model.registry.ObjectList

class ToolPaneTypeList(override val objects: MutableList<ToolPane.Type>) : ObjectList<ToolPane.Type>() {
    override val objectType: String
        get() = "ToolPane"
}