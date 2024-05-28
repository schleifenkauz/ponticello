package xenakis.model

import hextant.core.editor.ViewManager
import javafx.geometry.HorizontalDirection
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import xenakis.impl.ScWriter
import xenakis.impl.getSerializableValue
import xenakis.impl.putSerializableValue
import xenakis.sc.Bus
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.ui.EnvelopeObjectView

class EnvelopeObject(
    name: String, spec: NumericalControlSpec,
    var bus: Bus, val envelope: Envelope
) : AbstractScoreObject(name) {
    override val type: String
        get() = "envelope"

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

    override fun copy(): ScoreObject = EnvelopeObject(name, spec, bus, envelope.copy())

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject =
        EnvelopeObject(name, spec, bus, envelope.cut(position / duration, whichHalf))

    override fun writeStartCode(writer: ScWriter, offset: Double) {
        val env = envelope.code(offset)
        writer.append("{ $env }.play(s, ${bus.variableName});")
    }

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("spec", spec)
        putSerializableValue("bus", bus)
        putSerializableValue("envelope", envelope)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "envelope"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val spec = getSerializableValue<NumericalControlSpec>("spec")!!
            val bus = getSerializableValue<Bus>("bus")!!
            val envelope = getSerializableValue<Envelope>("envelope")!!
            return EnvelopeObject(name, spec, bus, envelope)
        }
    }
}