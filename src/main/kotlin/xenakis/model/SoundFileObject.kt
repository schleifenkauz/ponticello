package xenakis.model

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.FileSerializer
import xenakis.impl.ScWriter
import xenakis.impl.UDPSuperColliderClient
import xenakis.impl.superColliderPath
import xenakis.sc.Bus
import xenakis.sc.ControlSpec
import xenakis.sc.Envelope
import xenakis.sc.ParameterDef
import xenakis.ui.format
import java.io.File
import javax.sound.sampled.AudioSystem

@Serializable
class SoundFileObject(
    override var name: String,
    @Serializable(with = FileSerializer::class) val file: File, var outBus: Bus,
    var startPos: Double, var rate: Double,
    var envelope: Envelope,
    override var start: Double, override var y: Double,
    override var duration: Double, override var height: Double,
    override var muted: Boolean
) : ScoreObject() {
    override val color: Color?
        get() = null

    private val bufferName = "~sample_${file.nameWithoutExtension}"

    private val synthName = "~playbuf_${name}_${hashCode()}"

    override val controls: List<ParameterControl>
        get() = listOf(EnvelopeControl("amp", envelope, Color.WHITE, display = true))

    override fun getSpec(parameter: String): ControlSpec =
        if (parameter == "amp") ParameterDef.amp.spec else super.getSpec(parameter)

    override fun initialize(project: XenakisProject) {
        super.initialize(project)
        loadBuffer(project.client)
    }

    private fun loadBuffer(client: UDPSuperColliderClient) {
        client.postAsync("if ($bufferName == nil) { $bufferName = Buffer.read(s, ${file.superColliderPath}); }")
    }

    override fun onRemove() {
        val client = context[UDPSuperColliderClient]
        freeBuffer(client)
    }

    fun reloadFile() {
        val client = context[UDPSuperColliderClient]
        freeBuffer(client)
        loadBuffer(client)
    }

    private fun freeBuffer(client: UDPSuperColliderClient) {
        client.postAsync("$bufferName.free; $bufferName = nil;")
    }

    override fun writeStartCode(writer: ScWriter, offset: Double) = with(writer) {
        append("$synthName = { ")
        append("PlayBuf.ar($bufferName.numChannels, $bufferName, ")
        append("rate: ${rate.format(2)}, ")
        append("startPos: $startPos)")
        appendLine(" }.play(s, ${outBus.variableName});")
    }

    override fun writeStopCode(writer: ScWriter) {
        writer.appendLine("$synthName.release;")
    }

    override fun clone(newName: String): SoundFileObject = SoundFileObject(
        newName, file, outBus, startPos, rate, envelope.clone(),
        start, y, duration, height,
        muted
    )

    companion object {
        fun getDuration(file: File): Double {
            val stream = AudioSystem.getAudioInputStream(file)
            val format = stream.format
            return (stream.frameLength / format.frameRate).toDouble()
        }
    }
}