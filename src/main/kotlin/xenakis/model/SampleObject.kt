package xenakis.model

import hextant.context.Context
import javafx.scene.input.DataFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.event.unitEvent
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.FileSerializer
import xenakis.impl.ScWriter
import xenakis.impl.duration
import xenakis.impl.superColliderPath
import xenakis.model.SuperColliderObject.LiveCycleType
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

//TODO resolve files relative to project location (or simply copy them to the samples directory...)
@Serializable
class SampleObject(
    override val mutableName: ReactiveVariable<String>,
    var referencedFile: @Serializable(with = FileSerializer::class) File
) : AbstractSuperColliderObject() {
    override val variableName: String
        get() = "~sample_${name.now}"

    private val samplesDir
        get() = context[XenakisProject.projectDirectory].resolve("samples")
            .also { d -> if (!d.isDirectory) d.mkdir() }

    val wavFile get() = referencedFile //samplesDir.resolve("${name.now}.wav")
    val spectrogramFile get() = samplesDir.resolve("${name.now}_spectrogram.png")

    var duration: Double = -1.0
        get() = if (field == -1.0) error("SampleObject not initialized") else field
        private set
    var channels: Int = 0
        get() = if (field == 0) error("SampleObject not initialized") else field
        private set
    var sampleRate = 0.0
        get() = if (field == 0.0) error("SampleObject not initialized") else field
        private set

    @Transient
    private val contentChange = unitEvent()

    val contentsChanged get() = contentChange.stream

    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.ServerBoot

    private fun <T> useAudioStream(block: (AudioInputStream?) -> T): T {
        if (!wavFile.isFile) return block(null)
        val stream = AudioSystem.getAudioInputStream(wavFile)
        return stream.use(block)
    }

    private fun updateInfos() {
        useAudioStream { s ->
            if (s != null) {
                duration = s.duration
                channels = s.format.channels
                sampleRate = s.format.sampleRate.toDouble()
            }
        }
    }

    override fun onAdded(context: Context) {
        setContext(context)
        updateSpectrogram()
    }

    override fun initialize(context: Context) {
        super.initialize(context)
        updateInfos()
    }

    override fun onRemoved() {
        super.onRemoved()
        spectrogramFile.delete()
    }

    fun loadFile(file: File) {
        referencedFile = file
        sync()
    }

    override fun canRenameTo(newName: String): Boolean = !context[SampleRegistry].has(newName)

    override fun rename(newName: String) {
        val oldWavFile = wavFile
        super.rename(newName)
        val oldSpectrogramFile = spectrogramFile
        if (oldWavFile.isFile) wavFile.renameTo(samplesDir.resolve("$newName.wav"))
        if (oldSpectrogramFile.isFile) oldSpectrogramFile.renameTo(spectrogramFile)
    }

    override fun ScWriter.allocateServerObject() {
        +"$variableName = Buffer.read(s, ${wavFile.superColliderPath})"
    }

    private fun createSpectrogram(): CompletableFuture<Unit> {
        val soxCommand = System.getenv().getOrDefault("sox_path", "sox")
        val command = arrayOf(
            soxCommand, wavFile.absolutePath,
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
        if (referencedFile.isFile) {
            //if (referencedFile.absolutePath != wavFile.absolutePath) {
            //referencedFile.copyTo(wavFile, overwrite = true)
            //}
            createSpectrogram().join()
        }
    }

    override fun createReference(): SampleObjectReference = SampleObjectReference(this)

    companion object {
        private val logger = Logger.getLogger("SampleObject")

        val DATA_FORMAT = DataFormat("sample")
    }
}