package xenakis.model

import hextant.core.editor.ViewManager
import xenakis.impl.ScWriter
import xenakis.sc.Bus
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.ui.EnvelopeObjectView

class EnvelopeObject(
    name: String, spec: NumericalControlSpec,
    var bus: Bus, val envelope: Envelope
) : ScoreObject(name) {
    override val viewManager = ViewManager.createWeakViewManager<EnvelopeObjectView>()

    var spec: NumericalControlSpec = spec
        set(value) {
            if (value == field) return
            field = value
            viewManager.notifyViews { updatedSpec() }
        }

    override val associatedEnvelopes: List<EnvelopeControl>
        get() = listOf(EnvelopeControl(name, envelope, spec.associatedColor, display = true))

    override fun getSpec(parameter: String): ControlSpec = if (parameter == name) spec else super.getSpec(parameter)

    override fun clone(): ScoreObject =
        EnvelopeObject(name, spec, bus, envelope.clone())

    override fun writeStartCode(writer: ScWriter, offset: Double) {
        val env = envelope.code(offset, duration)
        writer.append("{ $env }.play(s, ${bus.variableName});")
    }
}