package xenakis.ui.registry

import xenakis.model.obj.ProcessDefObject
import xenakis.model.registry.ProcessDefRegistry

class SearchableProcessDefListView(
    registry: ProcessDefRegistry, title: String
) : SearchableRegistryView<ProcessDefObject>(registry, title) {
    override fun createObject(name: String): ProcessDefObject {
        val obj = ProcessDefObject.newEmpty(name)
        registry.add(obj)
        return obj
    }
}