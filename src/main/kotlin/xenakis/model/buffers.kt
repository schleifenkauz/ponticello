package xenakis.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.event.EventStream
import reaktive.event.never
import reaktive.event.unitEvent
import reaktive.value.*
import reaktive.value.binding.map
import xenakis.impl.FileSerializer
import xenakis.impl.SuperColliderClient
import xenakis.impl.superColliderPath
import xenakis.sc.editor.AbstractRenamableObject
import xenakis.ui.XenakisController.Companion.currentProject
import java.io.File
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

@Serializable
sealed interface BufferObject : RenamableObject {
    val variableName get() = "~buf_${name.now}"

    val initializationCode: String

    val channels: ReactiveInt

    val contentsChanged: EventStream<Unit>

    fun getAudioStream(): AudioInputStream

    override fun createReference(): BufferObjectReference = BufferObjectReference(this)
}

@Serializable
object NoBuffer : BufferObject {
    override val name: ReactiveValue<String>
        get() = reactiveValue("<none>")

    override fun canRenameTo(newName: String): Boolean = false

    override fun rename(newName: String) {
        throw UnsupportedOperationException("NoBuffer cannot be renamed")
    }

    override val channels: ReactiveInt
        get() = reactiveValue(0)

    override val variableName: String
        get() = "0"

    override val contentsChanged: EventStream<Unit>
        get() = never()

    override fun getAudioStream(): AudioInputStream {
        throw UnsupportedOperationException("NoBuffer has no audio stream")
    }

    override val initializationCode: String
        get() = throw UnsupportedOperationException("NoBuffer cannot be initialized")

    override fun initialize(context: Context) {}
}

@Serializable
sealed class AbstractBuffer : AbstractRenamableObject(), BufferObject {
    override fun canRenameTo(newName: String): Boolean = context[currentProject].buffers.hasBuffer(newName)

    override fun rename(newName: String) {
        context[SuperColliderClient].run {
            +"~buf_$newName = $variableName"
            +"$variableName = nil"
        }
        super.rename(newName)
    }
}

@Serializable
data class FileBuffer(
    override val mutableName: ReactiveVariable<String>,
    private val sourceFile: ReactiveVariable<@Serializable(with = FileSerializer::class) File>,
) : AbstractBuffer() {
    private val contentChange = unitEvent()

    override val contentsChanged: EventStream<Unit>
        get() = contentChange.stream

    val referencedFile: ReactiveValue<File> get() = sourceFile

    fun loadFile(file: File) {
        sourceFile.set(file)
        contentChange.fire()
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        this.context[SuperColliderClient].run(initializationCode)
    }

    override fun getAudioStream(): AudioInputStream = getStream(sourceFile.now)

    override val channels: ReactiveInt
        get() = sourceFile.map { f -> getStream(f).use { s -> s.format.channels } }

    override val initializationCode: String
        get() = "$variableName = Buffer.read(s, ${referencedFile.now.superColliderPath})"

    companion object {
        private fun getStream(file: File): AudioInputStream = AudioSystem.getAudioInputStream(file)
    }
}

@Serializable
data class AllocatedBuffer(
    override val mutableName: ReactiveVariable<String>,
    override val channels: ReactiveVariable<Int>, val frames: ReactiveVariable<Int>
) : AbstractBuffer() {
    override val initializationCode: String
        get() = "$variableName = Buffer.alloc(s, ${channels.now}, ${frames.now})"

    override val contentsChanged: EventStream<Unit>
        get() = never()

    override fun getAudioStream(): AudioInputStream {
        val file = File.createTempFile("buffer_contents", ".wav")
        context[SuperColliderClient].eval("$variableName.write(${file.superColliderPath}, 'wav', 'int16');")
        //TODO somehow wait for the buffer to be written to disk
        return AudioSystem.getAudioInputStream(file)
    }
}