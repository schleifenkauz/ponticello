package xenakis.model.score

import kotlinx.serialization.SerialName
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.code
import xenakis.impl.copy
import xenakis.model.Settings
import xenakis.model.flow.ScoreObjectInfo
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.ProcessDefReference
import xenakis.sc.ControlSpec

class ProcessObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("processDef") val processDefRef: ReactiveVariable<ProcessDefReference>,
    override val controls: ParameterControlList
) : ScoreObject(), ParameterizedObject {
    override val type: String
        get() = "process"

    val processDef get() = processDefRef.now.get() ?: ProcessDefObject.unresolved(context)

    override val def: ParameterizedObjectDef
        get() = processDef

    override val associatedControls: Map<String, ParameterControl>
        get() = controls.controlMap

    override fun getSpec(parameter: String): ControlSpec? = super<ParameterizedObject>.getSpec(parameter)

    override fun writeCode(info: ScoreObjectInfo): String = code {
        appendBlock("Task", endLine = false) {
            val latency = context[Settings].serverLatency.get()
            +"$latency.wait"
            +"${processDefRef.now.superColliderName}.value()"
        }
        +".play"
    }

    override fun doClone(newName: String): ScoreObject =
        ProcessObject(reactiveVariable(newName), processDefRef.copy(), controls.copy())
}