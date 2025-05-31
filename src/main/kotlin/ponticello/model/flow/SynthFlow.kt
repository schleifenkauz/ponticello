package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.impl.writeCode
import ponticello.impl.zero
import ponticello.model.obj.NoSynthDef
import ponticello.model.obj.SynthDefObject
import ponticello.model.obj.SynthDefReference
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList
import ponticello.model.score.controls.writeSynthCode
import ponticello.sc.editor.SynthDefSelector
import reaktive.value.*
import reaktive.value.binding.and
import reaktive.value.binding.flatMap

@Serializable
class SynthFlow(
    private var defRef: ReactiveVariable<SynthDefReference>,
    override val controls: ParameterControlList,
) : ParameterizedAudioFlow() {
    @Transient
    lateinit var synthDefSelector: SynthDefSelector
        private set

    override val def: SynthDefObject
        get() = defRef.now.get() ?: NoSynthDef()

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

    override fun copy(): AudioFlow = SynthFlow(defRef.copy(), controls.copy())

    override fun writeCode(placement: NodePlacement): String = writeCode {
        val latency = zero // context[Settings].serverLatency.now
        writeSynthCode(
            this@SynthFlow, superColliderName.removePrefix("~"),
            cutoff = zero, placement, latency
        )
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