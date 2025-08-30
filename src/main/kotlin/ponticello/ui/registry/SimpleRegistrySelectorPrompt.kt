package ponticello.ui.registry

import ponticello.model.registry.NamedObject
import ponticello.model.registry.ObjectRegistry
import reaktive.value.now

class SimpleRegistrySelectorPrompt<O : NamedObject>(registry: ObjectRegistry<O>, title: String) :
    RegistrySelectorPrompt<O>(registry, title) {
    override fun createObject(name: String): O? = null

    override fun displayText(option: O): String = option.name.now
}