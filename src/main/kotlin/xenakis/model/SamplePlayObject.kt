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
import xenakis.sc.Warp
import xenakis.sc.editor.BusSelector
import xenakis.ui.SamplePlayObjectView
import xenakis.ui.format

class SamplePlayObject(
    name: String,
    val sample: SampleObjectReference,
    private val initialOut: BusObjectReference,
    var startPos: Double, var rate: Double,
    var envelope: Envelope
) : RegularScoreObject(name) {
    lateinit var outSelector: BusSelector
        private set

    private val out get() = if (initialized) outSelector.result.now else initialOut

    override val type: String
        get() = "sample"

    override val viewManager = ListenerManager.createWeakListenerManager<SamplePlayObjectView>()

    override val associatedControls: Map<String, ParameterControl>
        get() = mapOf("amp" to EnvelopeControl(envelope, Color.WHITE, display = true))

    override fun getSpec(parameter: String): ControlSpec =
        if (parameter == "amp") ParameterDefObject.amp.spec.now else super.getSpec(parameter)

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        sample.resolve(context)
        outSelector = BusSelector(
            context,
            preferredChannels = sample.get().channels, preferredRate = Rate.Audio,
            initialValue = initialOut
        )
    }

    override fun writeStartCode(writer: ScWriter, offset: Double, name: String) = with(writer) {
        val sample = sample.get()
        val bufferName = sample.variableName
        val outBusName = out.get().variableName
        val startPosSamples = startPos + offset * sample.sampleRate
        append("~synths['$name'] = { ")
        append("PlayBuf.ar(${bufferName}.numChannels, $bufferName, ")
        append("rate: BufRateScale.kr($bufferName) * ${rate.format(2)}, ")
        append("startPos: $startPosSamples, ")
        append("loop: 1)")
        append(" * ${envelope.code(offset, doneAction = "Done.freeSelf")} }")
        appendLine(".play(s, $outBusName);")
    }

    override fun copy(): SamplePlayObject = SamplePlayObject(name.now, sample, out, startPos, rate, envelope.copy())

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject {
        val startPos = if (whichHalf == HorizontalDirection.LEFT) startPos else startPos + position * rate
        val env = envelope.cut(position, whichHalf)
        return SamplePlayObject(name.now, sample, out, startPos, rate, env)
    }

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("sample", sample)
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
            val sample = getSerializableValue<SampleObjectReference>("sample")!!
            val out = getSerializableValue<BusObjectReference>("out")!!
            val startPos = getDouble("startPos") ?: 0.0
            val rate = getDouble("rate") ?: 1.0
            val envelope = getSerializableValue<Envelope>("envelope") ?: Envelope.default
            return SamplePlayObject(name, sample, out, startPos, rate, envelope)
        }
    }

    companion object {
        fun create(buffer: SampleObject, name: String, context: Context): SamplePlayObject {
            val duration = buffer.duration
            val env = Envelope.constant(1.0, duration, Warp.Linear)
            val out = context[BusRegistry].getDefault()
            val obj = SamplePlayObject(name, buffer.createReference(), out.createReference(), 0.0, 1.0, env)
            obj.duration = duration
            return obj
        }
    }
}