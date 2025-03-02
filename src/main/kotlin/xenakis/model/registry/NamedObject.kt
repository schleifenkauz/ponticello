package xenakis.model.registry

import hextant.context.Context
import reaktive.value.ReactiveValue
import xenakis.model.obj.ContextualObject

interface NamedObject: ContextualObject {
    val name: ReactiveValue<String>

    fun onAdded(context: Context) {}

    fun onRemoved() {}

    fun reference(): ObjectReference = ObjectReference(this)
}