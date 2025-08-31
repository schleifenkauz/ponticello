package ponticello.model.obj

import fxutils.SubWindow
import hextant.context.Context
import javafx.scene.control.ScrollPane
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import ponticello.impl.*
import ponticello.model.project.PonticelloProject.Companion.projectDirectory
import ponticello.model.registry.BufferRegistry
import ponticello.model.score.ObjectPosition
import ponticello.sc.client.ScWriter
import ponticello.ui.launcher.PonticelloFiles
import reaktive.event.unitEvent
import reaktive.value.*
import java.io.File
import java.util.concurrent.CompletableFuture

@Serializable
@SerialName("SoundFile")
class SampleObject(
    @SerialName("path") private val referencedFile: ReactiveVariable<String>,
    val meter: MeterObject = MeterObject.none(),
    val firstBeat: ReactiveVariable<Decimal> = reactiveVariable(zero),
) : BufferObject() {
    @Transient
    override var _name: ReactiveVariable<String>? = null

    override val superColliderName: String
        get() = "~sample_${name.now}"

    override val registry: BufferRegistry
        get() = context[BufferRegistry]

    private val samplesDir
        get() = context[projectDirectory].resolve("samples")
            .also { d -> if (!d.isDirectory) d.mkdir() }

    @Transient
    lateinit var audioFile: File
        private set

    fun filePath(): ReactiveString = referencedFile

    val spectrogramFile get() = samplesDir.resolve("${name.now}_spectrogram.png")

    val spectrogramImage by lazy { Image(spectrogramFile.inputStream()) }

    @Transient
    private var _duration: ReactiveVariable<Decimal> = reactiveVariable(-one(ObjectPosition.TIME_PRECISION))

    val duration: ReactiveValue<Decimal> get() = _duration

    @Transient
    var channels: Int = 0
        private set

    @Transient
    var sampleRate = 0.0
        private set

    @Transient
    private var infoUpdateJob: CompletableFuture<Unit>? = null

    val infosUpdated get() = infoUpdateJob?.isDone ?: false

    override fun channels(): Int = channels

    override fun frames(): Int = (duration.now * sampleRate).toInt()

    override fun duration(): ReactiveValue<Decimal> = duration

    override fun waitForInfos() {
        infoUpdateJob?.join()
    }

    @Transient
    private val contentChange = unitEvent()

    val contentsChanged get() = contentChange.stream

    fun updateInfos(duration: Decimal, channels: Int, sampleRate: Double) {
        this._duration.now = duration
        this.channels = channels
        this.sampleRate = sampleRate
        infoUpdateJob!!.complete(Unit)
        contentChange.fire()
        Logger.fine("Updated infos for sample '${name.now}' [$audioFile]", Logger.Category.Buffers)
    }

    override fun onAdded() {
        super.onAdded()
        if (registry.copyAudioFiles.now) {
            copyReferencedFileToSamplesDir()
        }
        updateSpectrogram()
    }

    override fun onLoadedIntoRegistry() {
        super.onLoadedIntoRegistry()
        if (audioFile.lastModified() > spectrogramFile.lastModified()) {
            updateSpectrogram()
        }
    }

    private fun copyReferencedFileToSamplesDir() {
        val file = referencedFile()
        if (!file.isFile) {
            Logger.error("Audio file referenced by sample '${name.now}' not found!", Logger.Category.Samples)
            return
        }
        if (file.canonicalPath == audioFileInSamplesDir().canonicalPath) return
        try {
            file.copyTo(audioFileInSamplesDir(), overwrite = true)
        } catch (e: Exception) {
            Logger.error("Error while copying sample '${name.now}' [$file] to samples directory", e)
        }
    }

    override fun initialize(context: Context) {
        super.initialize(context)
        resolveAudioFile()
        if (meter.isNone()) {
            readTempoMetadata()
        }
    }

    private fun resolveAudioFile() {
        audioFile = when (registry.copyAudioFiles.now) {
            true -> audioFileInSamplesDir()
            false -> referencedFile()
        }
    }

    private fun readTempoMetadata() {
        try {
            if (!referencedFile().isFile) return
            val metadata = AudioFileIO.read(referencedFile()).tag ?: return
            val bpm = metadata.getFirst(FieldKey.BPM).toIntOrNull() ?: return
            meter.beatsPerMinute.now = bpm.toDecimal()
            meter.beatsPerBar.now = metadata.getFirst("BPB").toIntOrNull() ?: 4
            meter.ticksPerBeat.now = metadata.getFirst("TPB").toIntOrNull() ?: 4
        } catch (ex: Exception) {
            Logger.error("Error while reading audio file metadata for $this", ex)
        }
    }

    private fun audioFileInSamplesDir(): File = samplesDir.resolve("${name.now}.${referencedFile().extension}")

    fun referencedFile(): File = resolvePath(PonticelloFiles.userHome, referencedFile.now)

    fun toggleCopyToSamplesDir(copy: Boolean) {
        if (copy) {
            copyReferencedFileToSamplesDir()
            audioFile = audioFileInSamplesDir()
        } else {
            audioFileInSamplesDir().delete()
            audioFile = referencedFile()
        }
    }

    override fun onRemoved() {
        super.onRemoved()
        spectrogramFile.delete()
        if (registry.copyAudioFiles.now) {
            audioFileInSamplesDir().delete()
        }
    }

    fun loadFile(file: File) {
        referencedFile.now = relativizePath(PonticelloFiles.userHome, file)
        sync()
    }

    override fun rename(newName: String) {
        val oldSpectrogramFile = spectrogramFile
        val oldAudioFile = audioFileInSamplesDir()
        super.rename(newName)
        if (oldSpectrogramFile.isFile) oldSpectrogramFile.renameTo(spectrogramFile)
        if (registry.copyAudioFiles.now && oldAudioFile.isFile) {
            oldAudioFile.renameTo(audioFileInSamplesDir())
        }
    }

    override fun ScWriter.createObject() {
        infoUpdateJob?.join()
        infoUpdateJob = CompletableFuture<Unit>()
        appendBlock("$superColliderName = Buffer.read(s, ${audioFile.superColliderPath}, action: ", endLine = false) {
            +"arg b"
            +"~ponticello_addr.sendMsg('/buffer_info', '${name.now}', b.duration, b.numChannels, b.sampleRate)"
        }
        appendLine(");")
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
        copyReferencedFileToSamplesDir()
        super.sync()
    }

    private fun updateSpectrogram() {
        if (audioFile.isFile) {
            createSpectrogram().join()
            Thread.sleep(10)
        }
    }

    fun showSpectrogram() {
        val image = ImageView(spectrogramImage)
        val scrollPane = ScrollPane(image)
        val window = SubWindow(scrollPane, "Spectrogram of sample '${name.now}'")
        window.show()
    }

    companion object {
        val SUPPORTED_AUDIO_FORMATS = arrayOf("wav", "flac", "aiff", "au", "ogg")

        private fun relativizePath(base: File, audioFile: File): String {
            val relativized = audioFile.relativeTo(base)
            return if (relativized != audioFile) "~/${relativized.invariantSeparatorsPath}"
            else audioFile.invariantSeparatorsPath
        }

        private fun resolvePath(base: File, path: String): File {
            return when {
                path.startsWith("~/") -> base.resolve(path.drop(2))
                else -> File(path)
            }.canonicalFile
        }

        fun create(name: String, audioFile: File): SampleObject {
            val referencedFile = relativizePath(PonticelloFiles.userHome, audioFile)
            return SampleObject(reactiveVariable(referencedFile)).withName(name)
        }
    }
}