package ponticello.model.live

import fxutils.drag.TypedDataFormat
import kotlinx.serialization.Serializable
import ponticello.model.obj.LiveObjectReference
import ponticello.model.registry.NamedObject
import reaktive.value.ReactiveValue
import reaktive.value.now

@Serializable
sealed interface LiveObject : NamedObject {
    //TODO distinguish between scheduled and active
    val isActive: ReactiveValue<Boolean>
    val quantization: QuantizationConfig

    fun play()

    fun pause()

    fun toggle() {
        if (!isActive.now) play()
        else pause()
    }

    fun reset()

    companion object {
        val DATA_FORMAT = TypedDataFormat<LiveObjectReference>("ponticello/live-task")
    }
}