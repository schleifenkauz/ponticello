package xenakis.sc.editor

import kotlinx.serialization.Serializable
import reaktive.value.reactiveVariable
import xenakis.model.obj.GroupObject
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectRegistry

@Serializable
class GroupSelector : ObjectSelector<GroupObject>() {
    override fun getRegistry(): ObjectRegistry<GroupObject> = context[GroupRegistry]

    override fun createNewObject(name: String): GroupObject = GroupObject(reactiveVariable(name))
}