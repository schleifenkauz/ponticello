package xenakis.ui.registry

import reaktive.value.now
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry

class SimpleSearchableRegistryView<O : NamedObject>(registry: ObjectRegistry<O>, title: String) :
    SearchableRegistryView<O>(registry, title) {
    override fun createObject(name: String): O? = null

    override fun displayText(option: O): String = option.name.now
}