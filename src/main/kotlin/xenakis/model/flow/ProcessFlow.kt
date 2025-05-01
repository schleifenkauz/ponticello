package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.and
import reaktive.value.binding.flatMap
import reaktive.value.now
import xenakis.impl.copy
import xenakis.impl.writeCode
import xenakis.impl.zero
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.ProcessDefReference
import xenakis.model.score.ParameterControlList
import xenakis.model.score.controls.writeProcessCode
import xenakis.sc.editor.ProcessDefSelector

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

    override fun getDefaultName(): ReactiveString = defRef.flatMap { ref -> ref.name }

    override fun copy(): AudioFlow = ProcessFlow(defRef.copy(), controls.copy())

    override fun writeCode(placement: NodePlacement): String = writeCode {
        writeProcessCode(this@ProcessFlow, superColliderName.removePrefix("~"), cutoff = zero, latency = zero)
    }
}