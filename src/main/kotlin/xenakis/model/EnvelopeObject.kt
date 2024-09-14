package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.code
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector
import xenakis.ui.EnvelopeObjectView

@Serializable
data class EnvelopeObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("spec") private var _spec: NumericalControlSpec,
    @SerialName("bus") private val _initialBusRef: ObjectReference?,
    @SerialName("envelope") val envelope: Envelope
) : ScoreObject() {
    override val type: String
        get() = "envelope"

    lateinit var busSelector: BusSelector
        private set

    val bus get() = if (initialized) busSelector.result.now else _initialBusRef

    @Transient
    override val viewManager = ListenerManager.createWeakListenerManager<EnvelopeObjectView>()

    private val envelopeControl: EnvelopeControl
        get() = EnvelopeControl(
            envelope,
            reactiveVariable(spec.associatedColor),
            display = reactiveVariable(true)
        )

    var spec: NumericalControlSpec
        get() = _spec
        set(value) {
            if (value == _spec) return
            _spec = value
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
        busSelector = BusSelector(context, preferredRate = Rate.Control, 1)
        if (_initialBusRef != null) {
            busSelector.selectInitial(_initialBusRef)
        }
    }

    override val associatedControls: Map<String, ParameterControl>
        get() = mapOf(name.now to envelopeControl)

    override fun getSpec(parameter: String): ControlSpec = if (parameter == name.now) spec else super.getSpec(parameter)

    override fun doClone(newName: String): ScoreObject =
        EnvelopeObject(reactiveVariable(newName), spec, bus, envelope.copy())

    override fun doCut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject =
        EnvelopeObject(reactiveVariable(newName), spec, bus, envelope.cut(position / duration, whichHalf))

    override fun writeCode(env: ScorePlayEnv, name: String, cutoff: Double): String = code {
        val envelopeCode = envelope.code(cutoff)
        append("{ $envelopeCode }.play(s, ${busSelector.result.now.get<BusObject>().variableName});")
    }
}