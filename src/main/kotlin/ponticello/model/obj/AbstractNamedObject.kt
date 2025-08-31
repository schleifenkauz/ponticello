package ponticello.model.obj

import kotlinx.serialization.Transient
import ponticello.model.registry.NamedObject
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveVariable

abstract class AbstractNamedObject : AbstractContextualObject(), NamedObject {
    @Transient
    private var _isAdded = reactiveVariable(false)

    override val isAdded: ReactiveBoolean
        get() = _isAdded

    override fun onLoadedIntoRegistry() {
        _isAdded.now = true
    }

    override fun onAdded() {
        _isAdded.now = true
    }

    override fun onRemoved() {
        _isAdded.now = false
    }

    override fun toString(): String {
        val name = try {
            "#${name.now}"
        } catch (e: IllegalStateException) { //no name available
            "<unnamed>"
        }
        return "${javaClass.simpleName} $name"
    }
}