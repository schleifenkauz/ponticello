package ponticello.model.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class AllocatedBufferObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val channels: ReactiveVariable<Int>, val duration: ReactiveVariable<Decimal>
) : BufferObject() {

    override val superColliderName get() = "~buf_${name.now}"

    override val registry: ObjectRegistry<*>
        get() = context[BufferRegistry]

    override fun ScWriter.createObject() {
        +"$superColliderName = Buffer.alloc(s, ${frames()}, ${channels()})"
    }

    override fun channels(): Int = channels.now

    override fun frames(): Int {
        val sampleRate = context[SuperColliderClient].sampleRate
        return (sampleRate * duration.now.toDouble()).toInt()
    }

    override fun duration(): ReactiveValue<Decimal> = duration

    companion object {
        fun create(name: String, channels: Int, duration: Decimal): AllocatedBufferObject =
            AllocatedBufferObject(reactiveVariable(name), reactiveVariable(channels), reactiveVariable(duration))
    }
}