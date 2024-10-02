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
import xenakis.impl.string
import xenakis.model.SuperColliderObject.LiveCycleType
import java.io.File
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

@Serializable
sealed class BufferObject : AbstractSuperColliderObject() {
    override val superColliderName get() = "~buf_${name.now}"

    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.ServerBoot

    abstract val channels: ReactiveInt

    abstract val frames: ReactiveInt

    private val rootDir
        get() = context[XenakisProject.projectDirectory].resolve("buffers")
            .also { d -> if (!d.isDirectory) d.mkdir() }

    private val wavFile get() = rootDir.resolve("${name.now}.wav")

    val spectrogramFile get() = rootDir.resolve("${name.now}_spectrogram.png")

    @Transient
    private val contentChange = unitEvent()

    val contentsChanged: EventStream<Unit> get() = contentChange.stream

    protected fun contentChanged() {
        contentChange.fire()
    }

    override fun canRenameTo(newName: String): Boolean = !context[BufferRegistry].has(newName)

    protected fun updated() {
        val file = wavFile
        val bufnum = context[SuperColliderClient].eval("$superColliderName.bufnum").join().toIntOrNull()
        if (bufnum == null) {
            Logger.severe("Could not get bufnum for Buffer ${name.now}", Logger.Category.SuperCollider)
            return
        }
        val status = context[SuperColliderClient].send("writeBuf", listOf(file, bufnum)).join().string
        if (status != "ok") {
            Logger.severe("Error while writing buffer ${name.now} to project directory", Logger.Category.SuperCollider)
            return
        }
        createSpectrogram(file)
    }

    private fun createSpectrogram(file: File) {
        val command = arrayOf("sox", file.name, "-n", "spectrogram", "-o", "$spectrogramFile")
        Runtime.getRuntime()
            .exec(command)
            .onExit().thenApply { proc ->
                val exitCode = proc.exitValue()
                if (exitCode == 0) Logger.fine("Created spectrogram for buffer ${name.now}", Logger.Category.Buffers)
                else Logger.severe("Non zero exit code $exitCode creating spectrogram for buffer ${name.now}")
            }
    }

    override fun rename(newName: String) {
        val wavFile = wavFile
        if (wavFile.isFile) wavFile.renameTo(rootDir.resolve("$newName.wav"))
        val spectrogramFile = rootDir.resolve("${name.now}_spectrogram.png")
        if (spectrogramFile.isFile) spectrogramFile.renameTo(rootDir.resolve("${newName}_spectrogram.wav"))
        super.rename(newName)
    }

    fun <T> useAudioStream(block: (AudioInputStream?) -> T): T {
        if (!wavFile.isFile) return block(null)
        val stream = AudioSystem.getAudioInputStream(wavFile)
        return stream.use(block)
    }

    enum class Type {
        File, Allocate, Reference
    }

    companion object {
        val DATA_FORMAT = DataFormat("buffer")

        val defaultBuffer = ReferencedBuffer(reactiveVariable("0"))
    }
}