package xenakis.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveInt
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ScWriter

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

    override fun ScWriter.allocateServerObject() {}

    override fun ScWriter.freeServerObject() {}

    override fun ScWriter.addToServer() {}

    override fun rename(newName: String) {
        mutableName.set(newName)
        sync()
    }

    override fun remove() {}

    override fun sync(writer: ScWriter) {
        val channels = client.eval("$variableName.numChannels").join()
        val frames = client.eval("if ($variableName != nil) { $variableName.numFrames } { nil }").join()
        _channels.set(channels.toIntOrNull() ?: 0)
        _frames.set(frames.toIntOrNull() ?: 0)
        contentChanged()
    }

    companion object {
        fun create(name: String) = ReferencedBuffer(reactiveVariable(name))
    }
}