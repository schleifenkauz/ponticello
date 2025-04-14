package xenakis.model.score

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.code
import xenakis.impl.copy
import xenakis.model.Settings
import xenakis.model.flow.ScoreObjectInfo
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.ProcessDefReference
import xenakis.model.registry.ProcessDefRegistry

@Serializable
class ProcessObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("processDef") val processDefRef: ReactiveVariable<ProcessDefReference>,
    override val controls: ParameterControlList,
) : ParameterizedScoreObject() {
    override val type: String
        get() = "process"

    val processDef get() = processDefRef.now.get() ?: ProcessDefObject.unresolved(context)

    override val def: ParameterizedObjectDef
        get() = processDef

    override fun initialize(context: Context) {
        super.initialize(context)
        processDefRef.now.resolve(context[ProcessDefRegistry])
        controls.initialize(context, this)
    }

    override fun writeCode(info: ScoreObjectInfo): String = code {
        appendBlock("Task", endLine = false) {
            val latency = context[Settings].serverLatency.get()
            +"$latency.wait"
            append("${processDefRef.now.superColliderName}.value(t: 0, duration: $duration")
            for (control in controls) {
                if (!def.hasParameter(control.name.now)) continue
                val name = control.name.now
                val arg = control.now.generateCodeFor(this@ProcessObject, control.spec.now!!)
                append(", $name: ")
                arg.code(writer, context)
            }
            appendLine(");")
        }
        +".play"
    }

    override fun doClone(newName: String): ScoreObject =
        ProcessObject(reactiveVariable(newName), processDefRef.copy(), controls.copy())
}