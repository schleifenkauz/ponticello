package xenakis.model.obj

import hextant.context.Context
import kotlinx.serialization.Transient
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.registry.NamedObject

abstract class AbstractNamedObject : AbstractContextualObject(), NamedObject {
    @Transient
    private var _isAdded = reactiveVariable(false)

    override val isAdded: ReactiveBoolean
        get() = _isAdded

    final override fun onLoadedIntoRegistry() {
        _isAdded.now = true
    }

    override fun onAdded(context: Context) {
        _isAdded.now = true
    }

    override fun onRemoved() {
        _isAdded.now = false
    }
}