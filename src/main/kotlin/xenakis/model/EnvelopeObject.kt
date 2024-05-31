package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.impl.getSerializableValue
import xenakis.impl.putSerializableValue
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector
import xenakis.ui.EnvelopeObjectView

class EnvelopeObject(
    name: String, spec: NumericalControlSpec,
    private val initialBus: BusObjectReference, val envelope: Envelope
) : RegularScoreObject(name) {
    override val type: String
        get() = "envelope"

    @Transient
    lateinit var busSelector: BusSelector
        private set

    val bus get() = busSelector.result.now

    @Transient
    override val viewManager = ListenerManager.createWeakListenerManager<EnvelopeObjectView>()

    private val envelopeControl: EnvelopeControl get() = EnvelopeControl(envelope, spec.associatedColor, display = true)

    var spec: NumericalControlSpec = spec
        set(value) {
            if (value == field) return
            field = value
            viewManager.notifyListeners { updatedSpec() }
        }

    override fun rename(newName: String) {
        viewManager.notifyListeners {
            removedControl(name.now, envelopeControl)
        }
        super.rename(newName)
        viewManager.notifyListeners {
            addedControl(newName, envelopeControl)
        }
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        initialBus.initialize(context)
        busSelector = BusSelector(context, preferredRate = Rate.Control, 1, initialBus)
    }

    override val associatedControls: Map<String, ParameterControl>
        get() = mapOf(name.now to envelopeControl)

    override fun getSpec(parameter: String): ControlSpec = if (parameter == name.now) spec else super.getSpec(parameter)

    override fun copy(): ScoreObject = EnvelopeObject(name.now, spec, bus, envelope.copy())

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject =
        EnvelopeObject(name.now, spec, bus, envelope.cut(position / duration, whichHalf))

    override fun writeStartCode(writer: ScWriter, offset: Double, suffixGenerator: SuffixGenerator) {
        val env = envelope.code(offset)
        writer.append("{ $env }.play(s, ${busSelector.result.now.get().variableName});")
    }

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("spec", spec)
        putSerializableValue("bus", busSelector)
        putSerializableValue("envelope", envelope)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "envelope"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val spec = getSerializableValue<NumericalControlSpec>("spec")!!
            val bus = getSerializableValue<BusObjectReference>("bus")!!
            val envelope = getSerializableValue<Envelope>("envelope")!!
            return EnvelopeObject(name, spec, bus, envelope)
        }
    }
}