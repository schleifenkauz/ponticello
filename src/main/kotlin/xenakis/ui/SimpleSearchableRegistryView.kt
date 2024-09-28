package xenakis.ui

import reaktive.value.now
import xenakis.model.NamedObject
import xenakis.model.ObjectRegistry

class SimpleSearchableRegistryView<O : NamedObject>(val registry: ObjectRegistry<O>, title: String) :
    SearchableRegistryView<O>(registry, title) {
    override fun createObject(name: String): O? = null

    override fun displayText(option: O): String = option.name.now
}