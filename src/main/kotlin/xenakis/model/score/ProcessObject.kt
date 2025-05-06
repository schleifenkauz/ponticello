package xenakis.model.score

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.impl.writeCode
import xenakis.model.Settings
import xenakis.model.flow.NodePlacement
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.ProcessDefReference
import xenakis.model.registry.ProcessDefRegistry
import xenakis.model.score.controls.writeProcessCode
import xenakis.sc.ControlSpec

@Serializable
class ProcessObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("processDef") val processDefRef: ReactiveVariable<ProcessDefReference>,
    override val controls: ParameterControlList,
) : ParameterizedScoreObject() {
    override val type: String
        get() = "process"

    override val superColliderPrefix: String
        get() = "~process_"

    val processDef get() = processDefRef.now.get() ?: ProcessDefObject.unresolved()

    override val def: ParameterizedObjectDef
        get() = processDef

    override fun getSpec(parameter: String): ControlSpec? = controls.getOrNull(parameter)?.spec?.now

    override fun initialize(context: Context) {
        super.initialize(context)
        processDefRef.now.resolve(context[ProcessDefRegistry])
        initializeControls()
    }

    override fun validate(): Boolean = controls.validate()

    override fun writeCode(uniqueName: String, placement: NodePlacement?, cutoff: Decimal, latency: Decimal): String = writeCode {
        writeProcessCode(
            this@ProcessObject, uniqueName,
            cutoff, context[Settings].serverLatency.get(),
        )
    }

    override fun doClone(newName: String): ScoreObject =
        ProcessObject(reactiveVariable(newName), processDefRef.copy(), controls.copy())
}