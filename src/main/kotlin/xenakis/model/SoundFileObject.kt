package xenakis.model

import hextant.core.editor.ViewManager
import hextant.serial.SnapshotAware
import javafx.geometry.HorizontalDirection
import javafx.scene.paint.Color
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.value.now
import xenakis.impl.*
import xenakis.sc.Bus
import xenakis.sc.ControlSpec
import xenakis.sc.ParameterDef
import xenakis.sc.editor.BusSelector
import xenakis.ui.SoundFileObjectView
import xenakis.ui.format
import java.io.File
import javax.sound.sampled.AudioSystem

class SoundFileObject(
    name: String,
    val file: File,
    var outBus: BusSelector,
    var startPos: Double, var rate: Double,
    var envelope: Envelope
) : AbstractScoreObject(name) {
    override val type: String
        get() = "sample"

    override val viewManager = ViewManager.createWeakViewManager<SoundFileObjectView>()

    private val bufferName = "~sample_${file.nameWithoutExtension}_${hashCode()}"

    private val synthName = "~playbuf_${name}_${hashCode()}"

    init {
        duration = getDuration(file)
    }

    override val associatedControls: Map<String, ParameterControl>
        get() = mapOf("amp" to EnvelopeControl(envelope, Color.WHITE, display = true))

    override fun getSpec(parameter: String): ControlSpec =
        if (parameter == "amp") ParameterDef.amp.spec else super.getSpec(parameter)

    override fun serverBooted(context: SuperColliderContext) {
        loadBuffer(context)
    }

    private fun loadBuffer(context: SuperColliderContext) {
        context.postAsync("$bufferName = Buffer.read(s, ${file.superColliderPath});")
    }

    override fun onRemove() {
        val client = context[UDPSuperColliderClient]
        freeBuffer(client)
    }

    fun reloadFile(context: SuperColliderContext) {
        val client = context
        freeBuffer(client)
        loadBuffer(client)
    }

    private fun freeBuffer(client: SuperColliderContext) {
        client.postAsync("$bufferName.free; $bufferName = nil;")
    }

    override fun writeStartCode(writer: ScWriter, offset: Double) = with(writer) {
        append("$synthName = { ")
        append("PlayBuf.ar($bufferName.numChannels, $bufferName, ")
        append("rate: ${rate.format(2)}, ")
        append("startPos: $startPos)")
        append(" * ${envelope.code(offset, doneAction = "Done.none")} }")
        appendLine(".play(s, ${outBus.result.now.variableName});")
    }

    override fun writeStopCode(writer: ScWriter) {
        writer.appendLine("$synthName.release;")
    }

    override fun copy(): SoundFileObject = SoundFileObject(name, file, outBus, startPos, rate, envelope.copy())

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject {
        val startPos = if (whichHalf == HorizontalDirection.LEFT) startPos else start + position
        val env = envelope.cut(position / duration, whichHalf)
        return SoundFileObject(name, file, outBus, startPos, rate, env)
    }

    override fun JsonObjectBuilder.saveToJson() {
        put("file", file.absolutePath)
        putSerializableValue("outBus", outBus.result.now)
        if (startPos != 0.0) {
            put("startPos", startPos)
        }
        if (rate != 1.0) {
            put("rate", rate)
        }
        if (envelope != Envelope.default) putSerializableValue("envelope", envelope)
    }

    companion object {
        fun getDuration(file: File): Double {
            val stream = AudioSystem.getAudioInputStream(file)
            val format = stream.format
            return (stream.frameLength / format.frameRate).toDouble()
        }
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "sample"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val file = getFile("file")
            val outBus = getSerializableValue<Bus>("outBus")!!
            val busEditor = BusSelector(SnapshotAware.Serializer.reconstructionContext, outBus)
            val startPos = getDouble("startPos") ?: 0.0
            val rate = getDouble("rate") ?: 1.0
            val envelope = getSerializableValue<Envelope>("envelope") ?: Envelope.default
            return SoundFileObject(name, file, busEditor, startPos, rate, envelope)
        }
    }
}