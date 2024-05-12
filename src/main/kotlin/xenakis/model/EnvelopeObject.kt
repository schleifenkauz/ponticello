package xenakis.model

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.ScWriter
import xenakis.sc.Bus
import xenakis.sc.ControlSpec
import xenakis.sc.Envelope
import xenakis.sc.NumericalControlSpec

@Serializable
class EnvelopeObject(
    override var name: String, var spec: NumericalControlSpec, var bus: Bus,
    val envelope: Envelope,
    override var start: Double, override var duration: Double,
    override var y: Double, override var height: Double,
    override var muted: Boolean = false
) : ScoreObject() {
    override val color: Color
        get() = spec.associatedColor

    override val controls: List<ParameterControl>
        get() = emptyList()

    override val associatedEnvelopes: List<EnvelopeControl>
        get() = listOf(EnvelopeControl(name, envelope, color, display = true))

    override fun getSpec(parameter: String): ControlSpec = if (parameter == name) spec else super.getSpec(parameter)

    override fun initialize(project: XenakisProject) {

    }

    override fun clone(newName: String): ScoreObject =
        EnvelopeObject(name, spec, bus, envelope.clone(), start, duration, y, height)

    override fun writeStartCode(writer: ScWriter, offset: Double) {
        val env = envelope.code(offset, duration)
        writer.append("{ $env }.play(s, ${this.bus.variableName});")
    }
}