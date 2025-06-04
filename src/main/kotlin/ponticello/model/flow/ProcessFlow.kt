package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.impl.writeCode
import ponticello.impl.zero
import ponticello.model.obj.ProcessDefObject
import ponticello.model.obj.ProcessDefReference
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList
import ponticello.model.score.controls.writeProcessCode
import ponticello.sc.editor.ProcessDefSelector
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.and
import reaktive.value.binding.flatMap
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class ProcessFlow(
    private var defRef: ReactiveVariable<ProcessDefReference>,
    override val controls: ParameterControlList,
) : ParameterizedAudioFlow() {
    @Transient
    lateinit var processDefSelector: ProcessDefSelector
        private set

    override val def: ProcessDefObject
        get() = defRef.now.get() ?: ProcessDefObject.unresolved()

    @Transient
    override lateinit var isValid: ReactiveValue<Boolean>
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        processDefSelector = ProcessDefSelector()
        processDefSelector.syncWith(defRef)
        processDefSelector.initialize(context)
        controls.initialize(context)
        isValid = defRef.flatMap(ProcessDefReference::isResolved) and controls.isValid
    }

    override fun copy(): AudioFlow = ProcessFlow(defRef.copy(), controls.copy())

    override fun writeCode(placement: NodePlacement): String = writeCode {
        writeProcessCode(this@ProcessFlow, superColliderName.removePrefix("~"), cutoff = zero, latency = zero)
    }

    companion object {
        fun create(def: ProcessDefObject, controls: ParameterControlList): ProcessFlow =
            ProcessFlow(reactiveVariable(def.reference()), controls)
    }
}