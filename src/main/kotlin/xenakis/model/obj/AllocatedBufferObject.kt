package xenakis.model.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.model.registry.BufferRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient

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

    override fun canRenameTo(newName: String): Boolean = !context[BufferRegistry].has(newName)

    override fun rename(newName: String) {
        super.rename(newName)
        sync()
    }

    companion object {
        fun create(name: String, channels: Int, duration: Decimal): AllocatedBufferObject =
            AllocatedBufferObject(reactiveVariable(name), reactiveVariable(channels), reactiveVariable(duration))
    }
}