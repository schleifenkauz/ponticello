package ponticello.model.player

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import javafx.application.Platform
import ponticello.impl.superColliderPath
import ponticello.impl.writeCode
import ponticello.model.instr.BusObject
import ponticello.model.obj.project
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.SERVER_OPTIONS
import ponticello.model.project.get
import ponticello.model.server.BufferRegistry
import ponticello.model.server.BusRegistry
import ponticello.model.server.SampleObject
import ponticello.sc.Identifier
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.impl.showDialog
import ponticello.ui.misc.SaveRecordingDialog
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Recorder(private val context: Context) {
    private val client = context[SuperColliderClient]

    private var pathOfLastRecording: File? = null

    private val active = reactiveVariable(false)
    val isActive: ReactiveBoolean get() = active

    private val recording = reactiveVariable(false)
    val isRecording: ReactiveBoolean get() = recording //isRecording -> isActive

    fun toggle() {
        when {
            !isActive.now -> {
                active.set(true)
                if (ScorePlayer.instances().any { p -> p.isScheduled.now }) {
                    startRecording()
                }
            }

            isRecording.now -> stopRecording()
            else -> startRecording()
        }
    }

    fun startingPlayback() {
        if (isActive.now && !isRecording.now) {
            scheduleRecording()
        }
    }

    fun pausingPlayback() {
        if (isActive.now && ScorePlayer.instances().none { p -> p.isScheduled.now }) {
            stopRecording()
        }
    }

    private fun startRecording() {
        val path = pathForNextRecording()
        pathOfLastRecording = path
        val recordedBus = getRecordedBus()
        client.run("s.record(${path.superColliderPath}, ${recordedBus.superColliderName}, ${recordedBus.channels.now})")
        recording.set(true)
    }

    private fun getRecordedBus(): BusObject {
        val options = context.project[SERVER_OPTIONS]
        return options.recordedBus.get() ?: context[BusRegistry].getOutput()
    }

    fun stopRecording() {
        active.set(false)
        if (!isRecording.now) return
        recording.set(false)
        client.run("s.stopRecording")
        Platform.runLater {
            val tmpFile = pathOfLastRecording
            if (tmpFile != null) {
                val defaultFileName = tmpFile.nameWithoutExtension
                val saveOptions = SaveRecordingDialog(defaultFileName).showDialog(context)
                if (saveOptions == null) {
                    tmpFile.delete()
                } else {
                    val recordingFile = tmpFile.resolveSibling(saveOptions.fileName)
                    tmpFile.renameTo(recordingFile)
                    if (saveOptions.addAsBuffer) {
                        addRecordedAudioAsSample(recordingFile)
                    }
                }
                pathOfLastRecording = null
            }
        }
    }

    private fun addRecordedAudioAsSample(file: File) {
        val name = Identifier.truncate(file.nameWithoutExtension)
        val samplePath = context.project.projectDirectory
            .resolve("samples").also(File::mkdir)
            .resolve("$name.wav")
        file.copyTo(samplePath)
        val obj = SampleObject.create(name, samplePath)
        context[BufferRegistry].add(obj)
    }

    private fun scheduleRecording() {
        val path = pathForNextRecording()
        pathOfLastRecording = path
        val bus = getRecordedBus()
        client.run("s.prepareForRecord(${path.superColliderPath}, ${bus.channels.now})")
        val settings = context.project[PLAYBACK_SETTINGS]
        val code = writeCode(group = false) {
            appendBlock("s.bind") {
                +"s.record(bus: ${bus.superColliderName})"
            }
        }
        client.sendAsync(
            "schedule", listOf(
                /*absolute: */false, /*time: */ settings.scLangLatency.now.toDouble(),
                /*player_id: */ -1, /*code: */ code, /*info: */ "Start recording"
            )
        )
        recording.set(true)
    }

    private fun pathForNextRecording(): File {
        val fileName = DateTimeFormatter.ofPattern("yyyy-MM-dd_kk-mm").format(LocalDateTime.now()) + ".wav"
        val path = context.project.projectDirectory
            .resolve("recordings").also { dir -> dir.mkdirs() }
            .resolve(fileName)
        return path
    }

    companion object : PublicProperty<Recorder> by publicProperty("Recorder")
}