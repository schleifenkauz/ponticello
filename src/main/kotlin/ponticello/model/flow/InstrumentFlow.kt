package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.NoInstrument
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.model.score.controls.ParameterControlList
import ponticello.sc.editor.InstrumentSelector
import reaktive.Reactive
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.and
import reaktive.value.binding.flatMap
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("InstrumentFlow")
class InstrumentFlow(
    private var defRef: ReactiveVariable<ObjectReference<InstrumentObject>>,
    override val controls: ParameterControlList,
) : ParameterizedAudioFlow() {
    override val active = reactiveVariable(true)

    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    lateinit var instrumentSelector: InstrumentSelector
        private set

    override fun getInstrument(): InstrumentObject = defRef.now.get() ?: NoInstrument()

    override val instrumentChanged: Reactive get() = defRef

    @Transient
    override lateinit var isValid: ReactiveValue<Boolean>
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        instrumentSelector = InstrumentSelector()
        instrumentSelector.syncWith(defRef)
        instrumentSelector.initialize(context)
        controls.initialize(context, this)
        isValid = controls.isValid and defRef.flatMap { it.isResolved }
    }

    override fun copy(): AudioFlow = InstrumentFlow(defRef.copy(), controls.copy())

    companion object {
        fun create(def: InstrumentObject, controls: ParameterControlList): InstrumentFlow =
            InstrumentFlow(reactiveVariable(def.reference()), controls)
    }
}