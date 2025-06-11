package ponticello.ui.dock

import fxutils.actions.ContextualizedAction
import ponticello.model.registry.ObjectList

class ActionList(override val objects: MutableList<ContextualizedAction>) : ObjectList<ContextualizedAction>() {
    override val objectType: String
        get() = "Action"
}