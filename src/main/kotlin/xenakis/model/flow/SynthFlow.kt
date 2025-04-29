package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.*
import reaktive.value.binding.and
import reaktive.value.binding.flatMap
import xenakis.impl.zero
import xenakis.model.obj.NoSynthDef
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.SynthDefObject
import xenakis.model.obj.SynthDefReference
import xenakis.model.registry.reference
import xenakis.model.score.ParameterControlList
import xenakis.model.score.controls.writeSynthCode
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.SynthDefSelector

@Serializable
class SynthFlow(
    private var defRef: ReactiveVariable<SynthDefReference>,
    override val controls: ParameterControlList,
) : ParameterizedAudioFlow() {
    constructor(def: SynthDefObject, controls: ParameterControlList) : this(reactiveVariable(def.reference()), controls)

    @Transient
    lateinit var synthDefSelector: SynthDefSelector
        private set

    val synthDef get() = defRef.now.get() ?: NoSynthDef()

    override val def: ParameterizedObjectDef
        get() = synthDef

    @Transient
    override lateinit var isValid: ReactiveValue<Boolean>
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        synthDefSelector = SynthDefSelector()
        synthDefSelector.syncWith(defRef)
        synthDefSelector.initialize(context)
        controls.initialize(context, this)
        isValid = controls.isValid and defRef.flatMap(SynthDefReference::isResolved)
    }

    override fun copy(): AudioFlow = SynthFlow(defRef, controls.copy())

    override fun writeCode(writer: ScWriter, placement: NodePlacement) {
        writer.writeSynthCode(this, superColliderName.removePrefix("~"), cutoff = zero, placement, latency = zero)
    }

    override fun getDefaultName(): ReactiveString = defRef.flatMap { ref -> ref.name }

    companion object {
        fun create(def: SynthDefObject, context: Context): SynthFlow {
            val controls = def.defaultControls(context, defaultBus = null)
            val flow = SynthFlow(reactiveVariable(def.reference()), ParameterControlList.from(controls))
            return flow
        }
    }
}