package xenakis.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveInt
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderClient

@Serializable
class ReferencedBuffer(override val mutableName: ReactiveVariable<String>) : BufferObject() {
    @Transient
    private val _channels: ReactiveVariable<Int> = reactiveVariable(0)
    override val channels: ReactiveInt get() = _channels

    @Transient
    private val _frames: ReactiveVariable<Int> = reactiveVariable(0)
    override val frames: ReactiveInt get() = _frames

    override val variableName: String
        get() = name.now

    override val initializationCode: String
        get() = ""

    override fun sync(client: SuperColliderClient) {
        val channels = client.eval("$variableName.numChannels").join()
        val frames = client.eval("$variableName ?? $variableName.numFrames").join()
        _channels.set(channels.toIntOrNull() ?: 0)
        _frames.set(frames.toIntOrNull() ?: 0)
        contentChange.fire()
    }

    override fun rename(newName: String) {
        mutableName.set(newName)
        sync(context[SuperColliderClient])
    }

    override fun onRemove() {}

    companion object {
        fun create(name: String) = ReferencedBuffer(reactiveVariable(name))
    }
}