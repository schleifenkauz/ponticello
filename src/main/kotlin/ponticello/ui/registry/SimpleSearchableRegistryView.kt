package ponticello.ui.registry

import ponticello.model.registry.NamedObject
import ponticello.model.registry.ObjectRegistry
import reaktive.value.now

class SimpleSearchableRegistryView<O : NamedObject>(registry: ObjectRegistry<O>, title: String) :
    SearchableRegistryView<O>(registry, title) {
    override fun createObject(name: String): O? = null

    override fun displayText(option: O): String = option.name.now
}