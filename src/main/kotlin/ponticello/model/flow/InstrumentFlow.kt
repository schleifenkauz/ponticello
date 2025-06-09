package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.impl.writeCode
import ponticello.impl.zero
import ponticello.model.obj.InstrumentObject
import ponticello.model.obj.InstrumentReference
import ponticello.model.obj.NoInstrument
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList
import ponticello.model.score.controls.writeSynthCode
import ponticello.sc.editor.InstrumentSelector
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.and
import reaktive.value.binding.flatMap
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("InstrumentFlow")
class InstrumentFlow(
    private var defRef: ReactiveVariable<InstrumentReference>,
    override val controls: ParameterControlList,
) : ParameterizedAudioFlow() {
    @Transient
    lateinit var instrumentSelector: InstrumentSelector
        private set

    override val def: InstrumentObject
        get() = defRef.now.get() ?: NoInstrument()

    @Transient
    override lateinit var isValid: ReactiveValue<Boolean>
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        instrumentSelector = InstrumentSelector()
        instrumentSelector.syncWith(defRef)
        instrumentSelector.initialize(context)
        controls.initialize(context, this)
        isValid = controls.isValid and defRef.flatMap(InstrumentReference::isResolved)
    }

    override fun copy(): AudioFlow = InstrumentFlow(defRef.copy(), controls.copy())

    override fun writeCode(placement: NodePlacement): String = writeCode {
        val latency = zero // context[Settings].serverLatency.now
        writeSynthCode(
            this@InstrumentFlow, superColliderName.removePrefix("~"),
            cutoff = zero, placement, latency, run = isActive.now
        )
    }

    companion object {
        fun create(def: InstrumentObject, controls: ParameterControlList): InstrumentFlow =
            InstrumentFlow(reactiveVariable(def.reference()), controls)
    }
}