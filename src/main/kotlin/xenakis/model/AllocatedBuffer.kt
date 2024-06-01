package xenakis.model

import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderClient
import xenakis.impl.async

@Serializable
data class AllocatedBuffer(
    override val mutableName: ReactiveVariable<String>,
    override val channels: ReactiveVariable<Int>, override val frames: ReactiveVariable<Int>
) : BufferObject() {
    override val initializationCode: String
        get() = "$variableName = Buffer.alloc(s, ${channels.now}, ${frames.now})"

    override fun sync(client: SuperColliderClient) {
        async {
            val exists = client.eval(variableName).join()
            if (exists == "nil") reallocate()
            else {
                val channelsOnServer = client.eval("$variableName.numChannels").join().toInt()
                val framesOnServer = client.eval("$variableName.numFrames").join().toInt()
                if (channelsOnServer != channels.now || framesOnServer != frames.now) {
                    reallocate()
                }
            }
        }
    }

    companion object {
        fun create(name: String, channels: Int, frames: Int) =
            AllocatedBuffer(reactiveVariable(name), reactiveVariable(channels), reactiveVariable(frames))
    }
}