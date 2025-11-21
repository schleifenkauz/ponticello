package ponticello.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.model.obj.RenamableObject
import ponticello.model.obj.superColliderName
import ponticello.model.obj.withName
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("AllocatedBuffer")
class AllocatedBufferObject(
    val channels: ReactiveVariable<Int>, val duration: ReactiveVariable<Decimal>
) : BufferObject() {
    @Transient
    override var _name: ReactiveVariable<String>? = null

    override fun superColliderName(objectName: String) = "~buf_${objectName}"

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

    override fun copy(): RenamableObject = AllocatedBufferObject(channels.copy(), duration.copy())

    companion object {
        fun create(name: String, channels: Int, duration: Decimal): AllocatedBufferObject =
            AllocatedBufferObject(reactiveVariable(channels), reactiveVariable(duration)).withName(name)
    }
}