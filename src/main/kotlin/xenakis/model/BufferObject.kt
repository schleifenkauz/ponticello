package xenakis.model

import javafx.scene.input.DataFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.event.EventStream
import reaktive.event.unitEvent
import reaktive.value.ReactiveInt
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderClient
import xenakis.impl.superColliderPath
import xenakis.model.SuperColliderObject.LiveCycleType
import java.io.File
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

@Serializable
sealed class BufferObject : AbstractSuperColliderObject() {
    override val variableName get() = "~buf_${name.now}"

    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.ServerBoot

    abstract val channels: ReactiveInt

    abstract val frames: ReactiveInt

    @Transient
    protected val contentChange = unitEvent()

    val contentsChanged: EventStream<Unit> get() = contentChange.stream

    override fun canRenameTo(newName: String): Boolean = !context[BufferRegistry].has(newName)

    protected open fun getAudioStream(): AudioInputStream? {
        val file = File.createTempFile("buffer_contents", ".wav")
        context[SuperColliderClient].eval("$variableName.write(${file.superColliderPath}, 'wav', 'int16');")
        //TODO somehow wait for the buffer to be written to disk
        return AudioSystem.getAudioInputStream(file)
    }

    fun <T> useAudioStream(block: (AudioInputStream?) -> T): T {
        val stream = getAudioStream()
        return if (stream == null) block(null) else stream.use(block)
    }

    override fun createReference(): BufferObjectReference = BufferObjectReference(this)

    enum class Type {
        File, Allocate, Reference
    }

    companion object {
        val DATA_FORMAT = DataFormat("buffer")

        val defaultBuffer = ReferencedBuffer(reactiveVariable("0"))
    }
}