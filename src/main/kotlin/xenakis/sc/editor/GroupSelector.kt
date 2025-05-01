package xenakis.sc.editor

import reaktive.value.reactiveVariable
import xenakis.model.obj.GroupObject
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectRegistry

class GroupSelector : ObjectSelector<GroupObject>() {
    override fun getList(): ObjectRegistry<GroupObject> = context[GroupRegistry]

    override fun createNewObject(name: String): GroupObject = GroupObject(reactiveVariable(name))
}