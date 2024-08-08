package xenakis.model

import hextant.core.editor.ListenerManager
import hextant.serial.EditorRoot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.value.now
import xenakis.impl.*
import xenakis.sc.editor.ScFunctionEditor
import xenakis.ui.TaskObjectView

class TaskObject(name: String, val code: EditorRoot<ScFunctionEditor>, var width: Double) : RegularScoreObject(name) {
    override val type: String
        get() = "task"

    override val viewManager = ListenerManager.createWeakListenerManager<TaskObjectView>()

    override fun copy(): ScoreObject = TaskObject(name.now, code.clone(), width)

    override fun writeCode(env: ScorePlayEnv, name: String, cutoff: Double): String = code {
        appendBlock("~tasks['$name'] = Task") {
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

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("code", code)
        put("width", width)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "task"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val code = getSerializableValue<EditorRoot<ScFunctionEditor>>("code")!!
            val width = getDouble("width")!!
            return TaskObject(name, code, width)
        }
    }
}