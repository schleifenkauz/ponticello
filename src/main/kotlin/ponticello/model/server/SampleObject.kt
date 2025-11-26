package ponticello.model.server

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
import ponticello.model.obj.superColliderName
import ponticello.model.obj.withName
import ponticello.model.project.PonticelloProject.Companion.projectDirectory
import ponticello.model.score.MeterObject
import ponticello.model.score.ObjectPosition
import ponticello.sc.client.ScWriter
import ponticello.ui.launcher.PonticelloFiles
import reaktive.event.unitEvent
import reaktive.value.*
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture

@Serializable
@SerialName("SoundFile")
class SampleObject(
    @SerialName("path") private val referencedFile: ReactiveVariable<String>,
    @SerialName("channelMapping") private var _channelMapping: List<Int> = emptyList(),
    val meter: MeterObject = MeterObject.none(),
    val firstBeat: ReactiveVariable<Decimal> = reactiveVariable(zero),
) : BufferObject() {
    @Transient
    override var _name: ReactiveVariable<String>? = null

    override fun superColliderName(objectName: String) = "~sample_${objectName}"

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

    val spectrogramImage by lazy {
        try {
            Image(spectrogramFile.inputStream())
        } catch (e: IOException) {
            Logger.error("Error while loading spectrogram for sample '${name.now}' [$audioFile]", e)
            null
        }
    }

    @Transient
    private var _duration: ReactiveVariable<Decimal> = reactiveVariable(-one(ObjectPosition.TIME_PRECISION))

    val duration: ReactiveValue<Decimal> get() = _duration

    @Transient
    var sampleRate = 0.0
        private set

    @Transient
    private val _sourceChannels = reactiveVariable(0)

    val sourceChannels: ReactiveInt get() = _sourceChannels

    var channelMapping: List<Int>
        get() = _channelMapping.ifEmpty { (0 until sourceChannels.now).toList() }
        set(value) {
            _channelMapping = value
            sync()
        }

    @Transient
    private var infoUpdateJob: CompletableFuture<Unit>? = null

    val infosUpdated get() = infoUpdateJob?.isDone ?: false

    override fun channels(): Int = if (_channelMapping.isNotEmpty()) _channelMapping.size else sourceChannels.now

    override fun frames(): Int = (duration.now * sampleRate).toInt()

    override fun duration(): ReactiveValue<Decimal> = duration

    override fun waitForInfos() {
        infoUpdateJob?.join()
    }

    @Transient
    private val contentChange = unitEvent()

    val contentsChanged get() = contentChange.stream

    fun updateInfos(duration: Decimal, nChannels: Int, sampleRate: Double) {
        this._duration.now = duration
        this._sourceChannels.now = nChannels
        this.sampleRate = sampleRate
        infoUpdateJob!!.complete(Unit)
        contentChange.fire()
        Logger.fine("Updated infos for sample '${name.now}' [$audioFile]", Logger.Category.Buffers)
        println("Updated infos for sample '${name.now}' ($nChannels source channels)")
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
        if (registry.copyAudioFiles.now && !audioFile.isFile) {
            copyReferencedFileToSamplesDir()
        }
        if (!spectrogramFile.isFile || audioFile.lastModified() > spectrogramFile.lastModified()) {
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
            if (referencedFile().extension == "wav") return
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
        val path = audioFile.superColliderPath
        +"var path = $path, snd_file = SoundFile.openRead(path)"
        if (_channelMapping.isEmpty()) {
            +"$superColliderName = Buffer.read(s, path)"
        } else {
            val channelList = _channelMapping.joinToString(", ", "[", "]")
            +"$superColliderName = Buffer.readChannel(s, path, channels: $channelList)"
        }
        +"postf(\"Sending buffer_info message for ${name.now}\\n\")" //TODO why doesn't this work???
        +"~ponticello_addr.sendMsg('/buffer_info', '${name.now}', snd_file.duration, snd_file.numChannels, snd_file.sampleRate)"
        +"snd_file.close"
    }

    override fun sync() {
        updateSpectrogram()
        copyReferencedFileToSamplesDir()
        super.sync()
    }

    private fun updateSpectrogram() {
        if (!audioFile.isFile) return
        val soxCommand = System.getenv().getOrDefault("sox_path", "sox")
        val command = arrayOf(
            soxCommand, audioFile.absolutePath,
            "-n", "remix", *channelMapping.map { i -> (i + 1).toString() }.toTypedArray(),
            "spectrogram", "-o", spectrogramFile.absolutePath
        )
        Runtime.getRuntime()
            .exec(command)
            .onExit().thenApply { proc ->
                val exitCode = proc.exitValue()
                if (exitCode == 0) Logger.fine("Created spectrogram for sample ${name.now}", Logger.Category.Samples)
                else Logger.severe("Non zero exit code $exitCode creating spectrogram for sample ${name.now}")
            }.join()
    }

    fun showSpectrogram() {
        if (spectrogramImage == null) {
            Logger.warn("No spectrogram file available for buffer '${name.now}'", Logger.Category.Buffers)
            return

        }
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