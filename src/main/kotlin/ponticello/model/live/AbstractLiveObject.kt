package ponticello.model.live

import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.model.obj.AbstractRenamableObject
import ponticello.model.obj.project
import ponticello.model.project.CLOCKS
import ponticello.model.project.get
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
sealed class AbstractLiveObject: AbstractRenamableObject(), LiveObject {
    private var playing = reactiveVariable(false)
    private var scheduled = reactiveVariable(false)

    override val isScheduled: ReactiveBoolean get() = scheduled
    override val isPlaying: ReactiveBoolean get() = playing

    override val quantization: QuantizationConfig = QuantizationConfig.createDefault()

    override fun play() {
        if (isScheduled.now) error("$this is Already Active")
        scheduled.now = true
        val clock = quantization.clock.now.get() ?: context.project[CLOCKS].getDefault()
        clock.scheduleAction(quantization) { delay ->
            playing.now = true
            doActivate(delay)
        }
    }

    override fun pause() {
        if (!isScheduled.now) error("$this is not Active")
        scheduled.now = false
        playing.now = false
        doDeactivate()
    }

    override fun reset() {
        if (isScheduled.now) pause()
        doReset()
    }

    protected abstract fun doActivate(delay: Decimal)

    protected abstract fun doDeactivate()

    protected abstract fun doReset()
}