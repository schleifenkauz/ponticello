package xenakis.ui.registry

import reaktive.value.reactiveVariable
import xenakis.model.obj.GroupObject
import xenakis.model.registry.ObjectRegistry

class SearchableGroupListView(
    registry: ObjectRegistry<GroupObject>,
    title: String
) : SearchableRegistryView<GroupObject>(registry, title) {
    override fun createObject(name: String): GroupObject = GroupObject(reactiveVariable(name))
}