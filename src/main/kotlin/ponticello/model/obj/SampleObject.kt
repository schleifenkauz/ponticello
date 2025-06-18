package ponticello.model.obj

import fxutils.SubWindow
import hextant.context.Context
import javafx.scene.control.ScrollPane
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

@Serializable
class SampleObject(
    private val referencedFile: ReactiveVariable<String>,
) : BufferObject() {
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
    var duration: ReactiveVariable<Decimal> = reactiveVariable(-one(ObjectPosition.TIME_PRECISION))

    @Transient
    var channels: Int = 0

    @Transient
    var sampleRate = 0.0

    override fun channels(): Int = channels

    override fun frames(): Int = (duration.now * sampleRate).toInt()

    override fun duration(): ReactiveValue<Decimal> = duration

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
            duration.now = s.duration.asTime
            channels = s.format.channels
            sampleRate = s.format.sampleRate.toDouble()
        }
    }

    override fun onAdded() {
        super.onAdded()
        if (registry.copyAudioFiles.now) {
            copyReferencedFileToSamplesDir()
            updateInfos()
        }
        updateSpectrogram()
    }

    override fun onLoadedIntoRegistry() {
        super.onLoadedIntoRegistry()
        if (audioFile.lastModified() > spectrogramFile.lastModified()) {
            updateSpectrogram()
            updateInfos()
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
        if (!registry.copyAudioFiles.now || audioFileInSamplesDir().isFile) {
            updateInfos()
        }
    }

    private fun resolveAudioFile() {
        audioFile = when (registry.copyAudioFiles.now) {
            true -> audioFileInSamplesDir()
            false -> referencedFile()
        }
    }

    private fun audioFileInSamplesDir(): File = samplesDir.resolve("${name.now}.wav")

    private fun referencedFile(): File = resolvePath(PonticelloFiles.userHome, referencedFile.now)

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
        copyReferencedFileToSamplesDir()
        updateInfos()
        contentChange.fire()
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