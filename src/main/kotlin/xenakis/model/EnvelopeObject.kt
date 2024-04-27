package xenakis.model

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.ColorSerializer
import xenakis.impl.ScWriter
import xenakis.sc.Bus
import xenakis.sc.Envelope
import xenakis.sc.NumericalControlSpec

@Serializable
class EnvelopeObject(
    override var name: String, @Serializable(with = ColorSerializer::class) override val color: Color,
    var bus: Bus, val envelope: Envelope, var spec: NumericalControlSpec,
    override var start: Double, override var duration: Double,
    override var y: Double,
    override var height: Double
) : ScoreObject() {

    override val controls: List<ParameterControl>
        get() = emptyList()

    override val associatedEnvelopes: List<EnvelopeControl>
        get() = listOf(EnvelopeControl(name, envelope, spec, color, display = true))

    override fun initialize(project: XenakisProject) {

    }

    override fun clone(newName: String): ScoreObject =
        EnvelopeObject(name, color, bus, envelope.clone(), spec, start, duration, y, height)

    override fun ScWriter.writeStartCode(offset: Double) {
        val env = envelope.code(offset, duration)
        append("{ $env }.play(s, ${bus.code});")
    }

    override fun writeStopCode(writer: ScWriter) {

    }
}