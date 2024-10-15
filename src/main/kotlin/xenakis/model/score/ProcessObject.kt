package xenakis.model.score

import kotlinx.serialization.SerialName
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.code
import xenakis.impl.copy
import xenakis.model.Settings
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.ProcessDefObject
import xenakis.model.player.ScorePlayEnv
import xenakis.model.registry.ObjectReference

class ProcessObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("processDef") val processDefRef: ReactiveVariable<ObjectReference>,
    override val controls: ParameterControls
) : ParameterizedScoreObject() {
    override val type: String
        get() = "process"

    val processDef get() = processDefRef.now.get<ProcessDefObject>()

    override val def: ParameterizedObjectDef
        get() = processDef

    override fun writeCode(name: String, position: ObjectPosition, env: ScorePlayEnv): String = code {
        appendBlock("Task", endLine = false) {
            val latency = context[Settings].serverLatency.get()
            +"$latency.wait"
            +"${processDef.superColliderName}.value()"
        }
        +".play"
    }

    override fun doClone(newName: String): ScoreObject =
        ProcessObject(reactiveVariable(newName), processDefRef.copy(), controls.copy())
}