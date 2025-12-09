package ponticello.model.obj

import ponticello.model.registry.NamedObjectList
import reaktive.value.ReactiveValue

interface NamedObject : ContextualObject {
    val name: ReactiveValue<String>

    val canDelete: Boolean get() = true

    val registry: NamedObjectList<*>? get() = null

    fun copy(): NamedObject = throw UnsupportedOperationException("Cannot copy $this")

    companion object {
        const val NO_NAME = "<no name>"
    }
}