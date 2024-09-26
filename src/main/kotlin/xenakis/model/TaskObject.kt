package xenakis.model

import hextant.serial.EditorRoot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.code
import xenakis.sc.editor.ScFunctionEditor

@Serializable
class TaskObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val code: EditorRoot<ScFunctionEditor>,
    var width: Double
) : ScoreObject() {
    override val type: String
        get() = "task"

    override fun doClone(newName: String): ScoreObject = TaskObject(reactiveVariable(newName), code.clone(), width)

    override fun writeCode(name: String, position: ObjectPosition, env: ScorePlayEnv): String = code {
        appendBlock("~tasks['$name'] = Task") {
            +"${env.serverLatency}.wait"
            val function = code.editor.result.now
            function.code(writer, context)
            appendLine(".value()")
        }
        appendLine(".play;")
        appendBlock("SystemClock.sched(${duration})") {
            appendLine("~tasks['$name'].stop;")
        }
        appendLine(";")
    }
}