package xenakis.model.registry

import hextant.context.Context
import reaktive.value.ReactiveValue
import xenakis.model.obj.ContextualObject

interface NamedObject: ContextualObject {
    val name: ReactiveValue<String>

    val canDelete: Boolean get() = true

    fun onAdded(context: Context) {}

    fun onRemoved() {}

    fun reference(): ObjectReference = ObjectReference(this)
}