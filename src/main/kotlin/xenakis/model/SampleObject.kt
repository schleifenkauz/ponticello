package xenakis.model

import hextant.context.Context
import javafx.scene.input.DataFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.event.unitEvent
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.impl.duration
import xenakis.impl.superColliderPath
import xenakis.model.SuperColliderObject.LiveCycleType
import xenakis.model.XenakisProject.Companion.projectDirectory
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

//TODO resolve files relative to project location (or simply copy them to the samples directory...)
@Serializable
class SampleObject private constructor(
    override val mutableName: ReactiveVariable<String>,
    private var referencedFile: String
) : AbstractSuperColliderObject() {
    override val variableName: String
        get() = "~sample_${name.now}"

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

    @Transient
    var duration: Double = -1.0

    @Transient
    var channels: Int = 0

    @Transient
    var sampleRate = 0.0

    @Transient
    private val contentChange = unitEvent()

    val contentsChanged get() = contentChange.stream

    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.ServerBoot

    private fun <T> useAudioStream(block: (AudioInputStream) -> T): T? {
        if (!audioFile.isFile) {
            logger.severe("No audio stream found for sample ${name.now}: $referencedFile")
            return null
        }
        val stream = AudioSystem.getAudioInputStream(audioFile)
        return stream.use(block)
    }

    private fun updateInfos() {
        useAudioStream { s ->
            duration = s.duration
            channels = s.format.channels
            sampleRate = s.format.sampleRate.toDouble()
        }
    }

    override fun onAdded(context: Context) {
        setContext(context)
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
        val extension = audioFile.extension
        val oldAudioFile = audioFile
        super.rename(newName)
        val oldSpectrogramFile = spectrogramFile
        if (oldAudioFile.isFile) audioFile.renameTo(samplesDir.resolve("$newName.$extension"))
        if (oldSpectrogramFile.isFile) oldSpectrogramFile.renameTo(spectrogramFile)
    }

    override fun ScWriter.allocateServerObject() {
        +"$variableName = Buffer.read(s, ${audioFile.superColliderPath})"
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
                if (exitCode == 0) logger.info("Created spectrogram for buffer ${name.now}")
                else logger.severe("Non zero exit code $exitCode creating spectrogram for buffer ${name.now}")
            }
    }

    override fun sync(writer: ScWriter) {
        updateSpectrogram()
        updateInfos()
        contentChange.fire()
        super.sync(writer)
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

    override fun createReference(): SampleObjectReference = SampleObjectReference(this)

    companion object {
        private val logger = Logger.getLogger("SampleObject")

        fun relativizePath(base: File, audioFile: File): String {
            return when {
                audioFile.startsWith(base) -> "./" + audioFile.relativeTo(base).path
                audioFile.startsWith(base.parentFile) -> "../" + audioFile.relativeTo(base.parentFile).path
                else -> audioFile.absolutePath
            }
        }

        fun create(project: XenakisProject, name: ReactiveVariable<String>, audioFile: File): SampleObject {
            val referencedFile = relativizePath(project.projectDirectory, audioFile)
            return SampleObject(name, referencedFile)
        }

        val DATA_FORMAT = DataFormat("sample")
    }
}