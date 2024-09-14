package xenakis.model

import kotlinx.serialization.Serializable
import reaktive.value.*
import xenakis.impl.FileSerializer
import xenakis.impl.ScWriter
import xenakis.impl.superColliderPath
import java.io.File
import java.util.logging.Logger

@Serializable
data class FileBuffer(
    override val mutableName: ReactiveVariable<String>,
    private val sourceFile: ReactiveVariable<@Serializable(with = FileSerializer::class) File>,
) : BufferObject() {
    val referencedFile: ReactiveValue<File> get() = sourceFile

    private val _frames = reactiveVariable(0)
    private val _channels = reactiveVariable(0)

    override val channels: ReactiveInt
        get() = _channels
    override val frames: ReactiveInt
        get() = _frames

    override fun ScWriter.allocateServerObject() {
        +"$superColliderName = Buffer.read(s, ${referencedFile.now.superColliderPath})"
        updated()
        contentChanged()
    }

    fun loadFile(file: File) {
        sourceFile.set(file)
        client.run { sync(writer) }
        updated()
        contentChanged()
    }

    override fun sync(writer: ScWriter) {
        super.sync(writer)
        updateInfo()
        contentChanged()
    }

    private fun updateInfo() {
        useAudioStream { stream ->
            _channels.set(stream?.format?.channels ?: 0)
            _frames.set(stream?.frameLength?.toInt() ?: 0)
        }
    }

    companion object {
        private val logger = Logger.getLogger("FileBuffer")

        fun create(file: File, name: String = file.nameWithoutExtension) =
            FileBuffer(reactiveVariable(name), reactiveVariable(file))
    }
}