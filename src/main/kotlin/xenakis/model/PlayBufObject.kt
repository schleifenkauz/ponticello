package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.scene.paint.Color
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.impl.getDouble
import xenakis.impl.getSerializableValue
import xenakis.impl.putSerializableValue
import xenakis.sc.ControlSpec
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector
import xenakis.ui.SoundFileObjectView
import xenakis.ui.format

class PlayBufObject(
    name: String,
    val buffer: BufferObjectReference,
    private val initialOut: BusObjectReference,
    var startPos: Double, var rate: Double,
    var envelope: Envelope
) : RegularScoreObject(name) {
    lateinit var outSelector: BusSelector
        private set

    private val out get() = outSelector.result.now

    override val type: String
        get() = "sample"

    override val viewManager = ListenerManager.createWeakListenerManager<SoundFileObjectView>()

    override val associatedControls: Map<String, ParameterControl>
        get() = mapOf("amp" to EnvelopeControl(envelope, Color.WHITE, display = true))

    override fun getSpec(parameter: String): ControlSpec =
        if (parameter == "amp") ParameterDefObject.amp.spec.now else super.getSpec(parameter)

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        buffer.resolve(context)
        outSelector = BusSelector(
            context,
            preferredChannels = buffer.get().channels.now, preferredRate = Rate.Audio,
            initialValue = initialOut
        )
    }

    override fun writeStartCode(writer: ScWriter, offset: Double, suffixGenerator: SuffixGenerator) = with(writer) {
        val bufferName = buffer.get().variableName
        val outBusName = out.get().variableName
        val synthName = "~playbuf_${name}${suffixGenerator.generateSuffix(this@PlayBufObject)}"
        append("$synthName = { ")
        append("PlayBuf.ar(${bufferName}.numChannels, $bufferName, ")
        append("rate: ${rate.format(2)}, ")
        append("startPos: $startPos, ")
        append("loop: 1)")
        append(" * ${envelope.code(offset, doneAction = "Done.none")} }")
        appendLine(".play(s, $outBusName);")
    }

    override fun writeStopCode(writer: ScWriter, suffixGenerator: SuffixGenerator) {
        val synthName = "~playbuf_${name}${suffixGenerator.getSuffix(this)}"
        writer.appendLine("$synthName.release;")
    }

    override fun copy(): PlayBufObject = PlayBufObject(name.now, buffer, out, startPos, rate, envelope.copy())

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject {
        val startPos = if (whichHalf == HorizontalDirection.LEFT) startPos else start + position
        val env = envelope.cut(position / duration, whichHalf)
        return PlayBufObject(name.now, buffer, out, startPos, rate, env)
    }

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("buffer", buffer)
        putSerializableValue("out", out)
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
            val buffer = getSerializableValue<BufferObjectReference>("buffer")!!
            val out = getSerializableValue<BusObjectReference>("out")!!
            val startPos = getDouble("startPos") ?: 0.0
            val rate = getDouble("rate") ?: 1.0
            val envelope = getSerializableValue<Envelope>("envelope") ?: Envelope.default
            return PlayBufObject(name, buffer, out, startPos, rate, envelope)
        }
    }
}