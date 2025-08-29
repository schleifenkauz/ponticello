package ponticello.model.live

import kotlinx.serialization.Serializable
import ponticello.model.obj.AbstractNamedObject
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
sealed class LiveObject: AbstractNamedObject() {
    private var _isActive = reactiveVariable(false)

    val isActive: ReactiveValue<Boolean> get() = _isActive

    abstract val quantization: Quantization

    fun activate() {
        if (isActive.now) error("$this is Already Active")
        _isActive.now = true
        doActivate()
    }

    fun deactivate() {
        if (!isActive.now) error("$this is not Active")
        _isActive.now = false
        doDeactivate()
    }

    fun toggleActive() {
        if (isActive.now) deactivate()
        else activate()
    }

    fun reset() {
        if (isActive.now) deactivate()
        doReset()
    }

    abstract fun sync()

    protected abstract fun doActivate()

    protected abstract fun doDeactivate()

    protected abstract fun doReset()
}