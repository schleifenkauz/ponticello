package xenakis.model

import hextant.core.editor.ListenerManager
import hextant.serial.EditorRoot
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.code
import xenakis.sc.editor.ScFunctionEditor
import xenakis.ui.ScorePlayer
import xenakis.ui.TaskObjectView

class TaskObject(
    override val mutableName: ReactiveVariable<String>,
    val code: EditorRoot<ScFunctionEditor>,
    var width: Double
) : ScoreObject() {
    override val type: String
        get() = "task"

    override val viewManager = ListenerManager.createWeakListenerManager<TaskObjectView>()

    override fun doClone(newName: String): ScoreObject = TaskObject(reactiveVariable(newName), code.clone(), width)

    override fun writeCode(env: ScorePlayEnv, name: String, cutoff: Double): String = code {
        appendBlock("~tasks['$name'] = Task") {
            +"${ScorePlayer.SERVER_LATENCY}.wait"
            val function = code.editor.result.now
            function.code(writer, context)
            appendLine(".value()")
        }
        appendLine(".play;")
        appendBlock("SystemClock.sched(${duration - cutoff})") {
            appendLine("~tasks['$name'].stop;")
        }
        appendLine(";")
    }
}