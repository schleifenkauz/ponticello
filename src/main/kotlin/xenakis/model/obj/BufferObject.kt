package xenakis.model.obj

import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.obj.SuperColliderObject.LiveCycleType
import xenakis.model.registry.BufferRegistry
import xenakis.sc.client.ScWriter

@Serializable
class BufferObject(
    override val mutableName: ReactiveVariable<String>,
    val channels: ReactiveVariable<Int>, val frames: ReactiveVariable<Int>
) : AbstractSuperColliderObject() {
    override val superColliderName get() = "~buf_${name.now}"

    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.ServerBoot

    override fun ScWriter.allocateServerObject() {
        +"$superColliderName = Buffer.alloc(s, ${frames.now}, ${channels.now})"
    }

    override fun canRenameTo(newName: String): Boolean = !context[BufferRegistry].has(newName)

    override fun rename(newName: String) {
        super.rename(newName)
        sync()
    }

    companion object {
        fun create(name: String, channels: Int, frames: Int): BufferObject =
            BufferObject(reactiveVariable(name), reactiveVariable(channels), reactiveVariable(frames))
    }
}