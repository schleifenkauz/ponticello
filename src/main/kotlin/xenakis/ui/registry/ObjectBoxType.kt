package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import javafx.scene.Node
import xenakis.model.registry.NamedObject

interface ObjectBoxType<O : NamedObject> {
    fun getContent(obj: O): List<Node>

    fun getActions(obj: O): List<ContextualizedAction>

    fun deleteObject(obj: O)

    fun configure(box: ObjectBoxList<O>.ObjectBox) {}
}