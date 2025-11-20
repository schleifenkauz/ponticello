package ponticello.model.obj

import ponticello.model.registry.NamedObjectList
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue

interface NamedObject : ContextualObject {
    val isAdded: ReactiveBoolean

    val name: ReactiveValue<String>

    val canDelete: Boolean get() = true

    val registry: NamedObjectList<*>? get() = null

    fun copy(): NamedObject = throw UnsupportedOperationException("Cannot copy $this")

    fun onAdded() {}

    fun onLoadedIntoRegistry() {}

    fun onRemoved() {}

    companion object {
        const val NO_NAME = "<no name>"
    }
}