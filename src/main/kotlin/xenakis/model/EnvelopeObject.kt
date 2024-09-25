package xenakis.model

import hextant.context.Context
import javafx.geometry.HorizontalDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.code
import xenakis.impl.copy
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.editor.BusSelector
import xenakis.ui.EnvelopeObjectView

@Serializable
data class EnvelopeObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("spec") private var initialSpec: NumericalControlSpec,
    @SerialName("bus") private val busRef: ReactiveVariable<ObjectReference>,
    @SerialName("envelope") val envelope: Envelope
) : ScoreObject() {
    override val type: String
        get() = "envelope"

    lateinit var busSelector: BusSelector
        private set

    val bus: BusObject get() = busRef.now.get()

    private val envelopeControl: EnvelopeControl
        get() = EnvelopeControl(
            envelope,
            reactiveVariable(spec.associatedColor),
            display = reactiveVariable(true)
        )

    var spec: NumericalControlSpec
        get() = initialSpec
        set(value) {
            if (value == initialSpec) return
            initialSpec = value
            notifyListeners<EnvelopeObjectView> { updatedSpec() }
        }

    override fun rename(newName: String) {
        notifyListeners<EnvelopeObjectView> { removedControl(name.now, envelopeControl) }
        super.rename(newName)
        notifyListeners<EnvelopeObjectView> { addedControl(newName, envelopeControl) }
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        busSelector = BusSelector(context, bus.rate.now, bus.channels.now, busRef)
    }

    override val associatedControls: Map<String, ParameterControl>
        get() = mapOf(name.now to envelopeControl)

    override fun getSpec(parameter: String): ControlSpec = if (parameter == name.now) spec else super.getSpec(parameter)

    override fun doClone(newName: String): ScoreObject =
        EnvelopeObject(reactiveVariable(newName), spec, busRef.copy(), envelope.copy())

    override fun doCut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject =
        EnvelopeObject(reactiveVariable(newName), spec, busRef.copy(), envelope.cut(position / duration, whichHalf))

    override fun writeCode(name: String, position: ObjectPosition, env: ScorePlayEnv): String = code {
        val envelopeCode = envelope.code()
        append("{ $envelopeCode }.play(s, ${busSelector.selected.now.get<BusObject>().superColliderName});")
    }
}