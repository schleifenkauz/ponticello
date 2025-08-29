package ponticello.model.live

import kotlinx.serialization.Serializable
import ponticello.model.obj.AbstractRenamableObject
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
sealed class AbstractLiveObject: AbstractRenamableObject(), LiveObject {
    private var _isActive = reactiveVariable(false)

    override val isActive: ReactiveValue<Boolean> get() = _isActive

    override fun play() {
        if (isActive.now) error("$this is Already Active")
        _isActive.now = true
        doActivate()
    }

    override fun pause() {
        if (!isActive.now) error("$this is not Active")
        _isActive.now = false
        doDeactivate()
    }

    override fun reset() {
        if (isActive.now) pause()
        doReset()
    }

    protected abstract fun doActivate()

    protected abstract fun doDeactivate()

    protected abstract fun doReset()
}