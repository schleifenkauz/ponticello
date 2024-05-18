package xenakis.model

import hextant.context.Context
import hextant.core.editor.ViewManager
import javafx.scene.paint.Color
import xenakis.impl.ScWriter
import xenakis.impl.UDPSuperColliderClient
import xenakis.impl.superColliderPath
import xenakis.sc.Bus
import xenakis.sc.ControlSpec
import xenakis.sc.ParameterDef
import xenakis.ui.SoundFileObjectView
import xenakis.ui.format
import java.io.File
import javax.sound.sampled.AudioSystem

class SoundFileObject(
    name: String,
    val file: File,
    var outBus: Bus,
    var startPos: Double, var rate: Double,
    var envelope: Envelope
) : ScoreObject(name) {
    override val viewManager = ViewManager.createWeakViewManager<SoundFileObjectView>()

    private val bufferName = "~sample_${file.nameWithoutExtension}"

    private val synthName = "~playbuf_${name}_${hashCode()}"

    init {
        duration = getDuration(file)
    }

    override val associatedEnvelopes: List<EnvelopeControl>
        get() = listOf(EnvelopeControl("amp", envelope, Color.WHITE, display = true))

    override fun getSpec(parameter: String): ControlSpec =
        if (parameter == "amp") ParameterDef.amp.spec else super.getSpec(parameter)

    override fun addToContainer(container: ScoreObjectContainer, context: Context) {
        super.addToContainer(container, context)
        loadBuffer(context[UDPSuperColliderClient])
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

    override fun clone(): SoundFileObject = SoundFileObject(name, file, outBus, startPos, rate, envelope.clone())

    companion object {
        fun getDuration(file: File): Double {
            val stream = AudioSystem.getAudioInputStream(file)
            val format = stream.format
            return (stream.frameLength / format.frameRate).toDouble()
        }
    }
}