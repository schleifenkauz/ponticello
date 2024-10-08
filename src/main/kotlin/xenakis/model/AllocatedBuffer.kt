package xenakis.model

import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ScWriter
import xenakis.impl.async

@Serializable
data class AllocatedBuffer(
    override val mutableName: ReactiveVariable<String>,
    override val channels: ReactiveVariable<Int>, override val frames: ReactiveVariable<Int>
) : BufferObject() {
    override fun ScWriter.allocateServerObject() {
        +"$superColliderName = Buffer.alloc(s, ${frames.now}, ${channels.now})"
    }

    override fun sync(writer: ScWriter) {
        async {
            val exists = client.eval(superColliderName).get()
            if (exists == "nil") super.sync(writer)
            else {
                val channelsOnServer = client.eval("$superColliderName.numChannels").get().toInt()
                val framesOnServer = client.eval("$superColliderName.numFrames").get().toInt()
                if (channelsOnServer != channels.now || framesOnServer != frames.now) {
                    super.sync(writer)
                }
            }
        }
        contentChanged()
    }

    companion object {
        fun create(name: String, channels: Int, frames: Int) =
            AllocatedBuffer(reactiveVariable(name), reactiveVariable(channels), reactiveVariable(frames))
    }
}