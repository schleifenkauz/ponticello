package xenakis.model.player

import hextant.context.Context
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.code
import xenakis.impl.superColliderPath
import xenakis.model.Settings
import xenakis.model.obj.BusObject
import xenakis.model.obj.SampleObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.SampleRegistry
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.XenakisController.Companion.currentProject
import xenakis.ui.prompt.NamePrompt
import xenakis.ui.prompt.YesNoPrompt
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Recorder(private val context: Context) {
    private val client = context[SuperColliderClient]

    private var pathOfLastRecording: File? = null

    var isActive = false
        private set

    fun toggleIsActive() {
        isActive = !isActive
        if (isActive && context[PlaybackManager].player.isPlaying) startRecording()
        else stopRecording()
    }

    fun startingPlayback() {
        if (isActive) scheduleRecording()
    }

    fun pausingPlayback() {
        if (isActive) pauseRecording()
    }

    private fun startRecording() {
        val path = pathForNextRecording()
        pathOfLastRecording = path
        val recordedBus = getRecordedBus()
        client.run("s.record(${path.superColliderPath}, ${recordedBus.superColliderName}, ${recordedBus.channels.now})")
    }

    private fun getRecordedBus(): BusObject {
        val options = context[currentProject].serverOptions
        return options.recordedBus?.get<BusObject>() ?: context[BusRegistry].getDefault()
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
        val name = NamePrompt(context[SampleRegistry], "Name for sample", "").showDialog(context) ?: return true
        val samplePath = context[currentProject].projectDirectory
            .resolve("samples").also(File::mkdir)
            .resolve("$name.wav")
        pathOfLastRecording!!.copyTo(samplePath)
        val obj = SampleObject.create(context[currentProject], reactiveVariable(name), samplePath)
        context[SampleRegistry].add(obj)
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
        val code = code {
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