package xenakis.model

import kotlinx.serialization.Serializable
import reaktive.value.*
import xenakis.impl.FileSerializer
import xenakis.impl.SuperColliderClient
import xenakis.impl.superColliderPath
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

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

    fun loadFile(file: File) {
        sourceFile.set(file)
        updateInfo()
        reallocate()
        contentChange.fire()
    }

    override fun getAudioStream(): AudioInputStream? = try {
        AudioSystem.getAudioInputStream(sourceFile.now)
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error while getting audio stream for file ${sourceFile.now}: ${e.message}", e)
        null
    }

    override val initializationCode: String
        get() = "$variableName = Buffer.read(s, ${referencedFile.now.superColliderPath})"

    override fun sync(client: SuperColliderClient) {
        updateInfo()
        client.run("if ($variableName == nil) { $initializationCode; };")
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