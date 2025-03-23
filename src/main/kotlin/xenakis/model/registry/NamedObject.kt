package xenakis.model.registry

import hextant.context.Context
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import xenakis.model.obj.ContextualObject

interface NamedObject : ContextualObject {
    val isAdded: ReactiveBoolean

    val name: ReactiveValue<String>

    val canDelete: Boolean get() = true

    val canCopy: Boolean get() = false

    val registry: ObjectRegistry<*>? get() = null

    fun copy(name: String): NamedObject = throw UnsupportedOperationException("Cannot copy $this")

    fun onAdded(context: Context) {}

    fun onLoadedIntoRegistry() {}

    fun onRemoved() {}

    companion object {
        const val NO_NAME = "<no name>"
    }
}