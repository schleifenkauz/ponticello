package xenakis.model.player

import fxutils.prompt.YesNoPrompt
import hextant.context.Context
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.superColliderPath
import xenakis.impl.writeCode
import xenakis.model.Settings
import xenakis.model.obj.BusObject
import xenakis.model.obj.SampleObject
import xenakis.model.project.SERVER_OPTIONS
import xenakis.model.project.get
import xenakis.model.registry.BufferRegistry
import xenakis.model.registry.BusRegistry
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.controls.NamePrompt
import xenakis.ui.impl.showDialog
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Recorder(private val context: Context) {
    private val client = context[SuperColliderClient]

    private var pathOfLastRecording: File? = null

    private val _isActive = reactiveVariable(false)
    val isActive: ReactiveBoolean get() = _isActive

    fun toggleIsActive() {
        _isActive.now = !isActive.now
        if (isActive.now && context[PlaybackManager].player.isPlaying.now) startRecording()
        else stopRecording()
    }

    fun startingPlayback() {
        if (isActive.now) scheduleRecording()
    }

    fun pausingPlayback() {
        if (isActive.now) pauseRecording()
    }

    private fun startRecording() {
        val path = pathForNextRecording()
        pathOfLastRecording = path
        val recordedBus = getRecordedBus()
        client.run("s.record(${path.superColliderPath}, ${recordedBus.superColliderName}, ${recordedBus.channels.now})")
    }

    private fun getRecordedBus(): BusObject {
        val options = context[currentProject][SERVER_OPTIONS]
        return options.recordedBus.get() ?: context[BusRegistry].getDefault()
    }

    fun stopRecording() {
        client.run("s.stopRecording")
        if (pathOfLastRecording != null) {
            if (YesNoPrompt("Add recorded audio as sample").showDialog(context) == true) {
                if (addRecordedAudioAsSample()) return
            }
            pathOfLastRecording = null
        }
    }

    private fun addRecordedAudioAsSample(): Boolean {
        val name = NamePrompt(context[BufferRegistry], "Name for sample", "").showDialog(context) ?: return true
        val samplePath = context[currentProject].projectDirectory
            .resolve("samples").also(File::mkdir)
            .resolve("$name.wav")
        pathOfLastRecording!!.copyTo(samplePath)
        val obj = SampleObject.create(context[currentProject], reactiveVariable(name), samplePath)
        context[BufferRegistry].add(obj)
        return false
    }

    private fun pauseRecording() {
        client.run("s.pauseRecording")
    }

    private fun scheduleRecording() {
        val path = pathForNextRecording()
        pathOfLastRecording = path
        val bus = getRecordedBus()
        client.run("s.prepareForRecord(${path.superColliderPath}, ${bus.channels.now})")
        val settings = context[Settings]
        val code = writeCode {
            val serverLatency = settings.serverLatency.now
            appendBlock("s.makeBundle($serverLatency)") {
                +"s.record(bus: ${bus.superColliderName})"
            }
        }
        client.send("schedule", listOf(settings.scLangLatency.now.toDouble(), code))
    }

    private fun pathForNextRecording(): File {
        val fileName = DateTimeFormatter.ofPattern("yyyy-MM-dd_kk-mm").format(LocalDateTime.now()) + ".wav"
        val path = context[currentProject].dataDir
            .resolve("tmp").also { tmp -> tmp.mkdirs() }
            .resolve(fileName)
        return path
    }
}