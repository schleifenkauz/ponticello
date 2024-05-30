package xenakis.model

import hextant.core.editor.ListenerManager
import hextant.serial.SnapshotAware
import javafx.geometry.HorizontalDirection
import javafx.scene.paint.Color
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.value.now
import xenakis.impl.*
import xenakis.sc.ControlSpec
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector
import xenakis.ui.SoundFileObjectView
import xenakis.ui.format
import java.io.File

class SoundFileObject(
    name: String,
    val file: File,
    var outBus: BusSelector,
    var startPos: Double, var rate: Double,
    var envelope: Envelope
) : RegularScoreObject(name) {
    override val type: String
        get() = "sample"

    override val viewManager = ListenerManager.createWeakListenerManager<SoundFileObjectView>()

    private val bufferName = "~sample_${file.nameWithoutExtension}_${hashCode()}"

    private val synthName = "~playbuf_${name}"

    override val associatedControls: Map<String, ParameterControl>
        get() = mapOf("amp" to EnvelopeControl(envelope, Color.WHITE, display = true))

    override fun getSpec(parameter: String): ControlSpec =
        if (parameter == "amp") ParameterDefObject.amp.spec.now else super.getSpec(parameter)

    override fun serverBooted(context: SuperColliderContext) {
        loadBuffer(context)
    }

    private fun loadBuffer(context: SuperColliderContext) {
        context.run("$bufferName = Buffer.read(s, ${file.superColliderPath});")
    }

    override fun onRemove() {
        val client = context[SuperColliderClient]
        freeBuffer(client)
    }

    fun reloadFile(context: SuperColliderContext) {
        val client = context
        freeBuffer(client)
        loadBuffer(client)
    }

    private fun freeBuffer(client: SuperColliderContext) {
        client.run("$bufferName.free; $bufferName = nil;")
    }

    override fun writeStartCode(writer: ScWriter, offset: Double, suffixGenerator: SuffixGenerator) = with(writer) {
        append("$synthName${suffixGenerator.generateSuffix(this@SoundFileObject)} = { ")
        append("PlayBuf.ar($bufferName.numChannels, $bufferName, ")
        append("rate: ${rate.format(2)}, ")
        append("startPos: $startPos)")
        append(" * ${envelope.code(offset, doneAction = "Done.none")} }")
        appendLine(".play(s, ${outBus.result.now.variableName});")
    }

    override fun writeStopCode(writer: ScWriter, suffixGenerator: SuffixGenerator) {
        writer.appendLine("$synthName${suffixGenerator.getSuffix(this)}.release;")
    }

    override fun copy(): SoundFileObject = SoundFileObject(name.now, file, outBus, startPos, rate, envelope.copy())

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject {
        val startPos = if (whichHalf == HorizontalDirection.LEFT) startPos else start + position
        val env = envelope.cut(position / duration, whichHalf)
        return SoundFileObject(name.now, file, outBus, startPos, rate, env)
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

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "sample"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val file = getFile("file")
            val outBus = getSerializableValue<BusObject>("outBus")!! //TODO this creates a duplicate bus!!
            val busEditor = BusSelector(
                SnapshotAware.Serializer.reconstructionContext,
                preferredRate = Rate.Audio,
                preferredChannels = 2, //TODO channel number of the file
                bus = outBus
            )
            val startPos = getDouble("startPos") ?: 0.0
            val rate = getDouble("rate") ?: 1.0
            val envelope = getSerializableValue<Envelope>("envelope") ?: Envelope.default
            return SoundFileObject(name, file, busEditor, startPos, rate, envelope)
        }
    }
}