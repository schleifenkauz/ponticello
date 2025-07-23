package ponticello.model.player

import bundles.PublicProperty
import bundles.publicProperty
import fxutils.prompt.YesNoPrompt
import hextant.context.Context
import javafx.application.Platform
import ponticello.impl.superColliderPath
import ponticello.impl.writeCode
import ponticello.model.GlobalSettings
import ponticello.model.obj.BusObject
import ponticello.model.obj.SampleObject
import ponticello.model.obj.project
import ponticello.model.project.SERVER_OPTIONS
import ponticello.model.project.get
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.BusRegistry
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.controls.NamePrompt
import ponticello.ui.impl.showDialog
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
            if (pathOfLastRecording != null) {
                if (YesNoPrompt("Add recorded audio as sample").showDialog(context) == true) {
                    addRecordedAudioAsSample()
                }
                pathOfLastRecording = null
            }
        }
    }

    private fun addRecordedAudioAsSample() {
        val name = NamePrompt(context[BufferRegistry], "Name for sample", "").showDialog(context) ?: return
        val samplePath = context.project.projectDirectory
            .resolve("samples").also(File::mkdir)
            .resolve("$name.wav")
        pathOfLastRecording!!.copyTo(samplePath)
        val obj = SampleObject.create(name, samplePath)
        context[BufferRegistry].add(obj)
    }

    private fun scheduleRecording() {
        val path = pathForNextRecording()
        pathOfLastRecording = path
        val bus = getRecordedBus()
        client.run("s.prepareForRecord(${path.superColliderPath}, ${bus.channels.now})")
        val settings = context[GlobalSettings]
        val code = writeCode {
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