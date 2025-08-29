package ponticello.model.live

import fxutils.drag.TypedDataFormat
import kotlinx.serialization.Serializable
import ponticello.model.obj.LiveObjectReference
import ponticello.model.registry.NamedObject
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import reaktive.value.now

@Serializable
sealed interface LiveObject : NamedObject {
    val isScheduled: ReactiveValue<Boolean>
    val isPlaying: ReactiveBoolean
    val quantization: QuantizationConfig


    override val registry: LiveObjectRegistry
        get() = context[LiveObjectRegistry]

    fun play()

    fun pause()

    fun toggle() {
        if (!isScheduled.now) play()
        else pause()
    }

    fun reset()

    companion object {
        val DATA_FORMAT = TypedDataFormat<LiveObjectReference>("ponticello/live-task")
    }
}