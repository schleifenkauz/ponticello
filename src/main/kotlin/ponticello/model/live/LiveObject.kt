package ponticello.model.live

import fxutils.drag.TypedDataFormat
import kotlinx.serialization.Serializable
import ponticello.model.obj.LiveObjectReference
import ponticello.model.obj.NamedObject
import ponticello.model.score.ScoreObject
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

    fun hasReferencesTo(obj: ScoreObject): Boolean = false

    companion object {
        val DATA_FORMAT = TypedDataFormat<LiveObjectReference>("ponticello/live-task")
    }
}