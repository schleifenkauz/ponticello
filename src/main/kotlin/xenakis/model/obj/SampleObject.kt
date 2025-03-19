package xenakis.model.obj

import hextant.context.Context
import javafx.scene.image.Image
import javafx.scene.input.DataFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.event.unitEvent
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.*
import xenakis.model.Logger
import xenakis.model.XenakisProject
import xenakis.model.XenakisProject.Companion.projectDirectory
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.SampleRegistry
import xenakis.model.score.ObjectPosition
import xenakis.sc.client.ScWriter
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

@Serializable
class SampleObject private constructor(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    private var referencedFile: String
) : AbstractSuperColliderObject() {
    override val superColliderName: String
        get() = "~sample_${name.now}"

    override val registry: ObjectRegistry<*>?
        get() = context[SampleRegistry]

    private val samplesDir
        get() = context[projectDirectory].resolve("samples")
            .also { d -> if (!d.isDirectory) d.mkdir() }

    @Transient
    lateinit var audioFile: File
        private set

    private fun resolveAudioFile(base: File) {
        audioFile = when {
            referencedFile.startsWith("../") -> base.parentFile.resolve(referencedFile.drop(3))
            referencedFile.startsWith("./") -> base.resolve(referencedFile.drop(2))
            else -> File(referencedFile)
        }.canonicalFile
    }

    val spectrogramFile get() = samplesDir.resolve("${name.now}_spectrogram.png")

    val spectrogramImage by lazy { Image(spectrogramFile.inputStream()) }

    @Transient
    var duration: Decimal = -one(ObjectPosition.TIME_PRECISION)

    @Transient
    var channels: Int = 0

    @Transient
    var sampleRate = 0.0

    @Transient
    private val contentChange = unitEvent()

    val contentsChanged get() = contentChange.stream

    private fun <T> useAudioStream(block: (AudioInputStream) -> T): T? {
        if (!audioFile.isFile) {
            Logger.severe("No audio stream found for sample ${name.now}: $referencedFile")
            return null
        }
        val stream = AudioSystem.getAudioInputStream(audioFile)
        return stream.use(block)
    }

    private fun updateInfos() {
        useAudioStream { s ->
            duration = s.duration.asTime
            channels = s.format.channels
            sampleRate = s.format.sampleRate.toDouble()
        }
    }

    override fun onAdded(context: Context) {
        super.onAdded(context)
        updateSpectrogram()
    }

    override fun initialize(context: Context) {
        resolveAudioFile(context[projectDirectory])
        super.initialize(context)
        updateInfos()
    }

    override fun onRemoved() {
        super.onRemoved()
        spectrogramFile.delete()
    }

    fun loadFile(file: File) {
        val base = context[projectDirectory]
        referencedFile = relativizePath(base, file)
        sync()
    }

    override fun canRenameTo(newName: String): Boolean = !context[SampleRegistry].has(newName)

    override fun rename(newName: String) {
        val oldSpectrogramFile = spectrogramFile
        super.rename(newName)
        if (oldSpectrogramFile.isFile) oldSpectrogramFile.renameTo(spectrogramFile)
    }

    override fun ScWriter.createObject() {
        +"$superColliderName = Buffer.read(s, ${audioFile.superColliderPath})"
    }

    private fun createSpectrogram(): CompletableFuture<Unit> {
        val soxCommand = System.getenv().getOrDefault("sox_path", "sox")
        val command = arrayOf(
            soxCommand, audioFile.absolutePath,
            "-n", "spectrogram", "-r",
            "-o", spectrogramFile.absolutePath
        )
        return Runtime.getRuntime()
            .exec(command)
            .onExit().thenApply { proc ->
                val exitCode = proc.exitValue()
                if (exitCode == 0) Logger.fine("Created spectrogram for sample ${name.now}", Logger.Category.Samples)
                else Logger.severe("Non zero exit code $exitCode creating spectrogram for sample ${name.now}")
            }
    }

    override fun ScWriter.sync() {
        updateSpectrogram()
        updateInfos()
        contentChange.fire()
        super.sync()
    }

    private fun updateSpectrogram() {
        if (audioFile.isFile) {
            //if (referencedFile.absolutePath != wavFile.absolutePath) {
            //referencedFile.copyTo(wavFile, overwrite = true)
            //}
            createSpectrogram().join()
            Thread.sleep(10)
        }
    }

    companion object {
        fun relativizePath(base: File, audioFile: File): String {
            return when {
                audioFile.startsWith(base) -> "./" + audioFile.relativeTo(base).invariantSeparatorsPath
                audioFile.startsWith(base.parentFile) -> "../" + audioFile.relativeTo(base.parentFile).invariantSeparatorsPath
                else -> audioFile.absoluteFile.invariantSeparatorsPath
            }
        }

        fun create(project: XenakisProject, name: ReactiveVariable<String>, audioFile: File): SampleObject {
            val referencedFile = relativizePath(project.projectDirectory, audioFile)
            return SampleObject(name, referencedFile)
        }

        val DATA_FORMAT = DataFormat("sample")
    }
}