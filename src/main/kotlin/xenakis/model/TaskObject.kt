package xenakis.model

import hextant.core.editor.ViewManager
import hextant.serial.EditorRoot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.impl.getDouble
import xenakis.impl.getSerializableValue
import xenakis.impl.putSerializableValue
import xenakis.sc.editor.ScFunctionEditor
import xenakis.ui.TaskObjectView

class TaskObject(name: String, val code: EditorRoot<ScFunctionEditor>, var width: Double) : AbstractScoreObject(name) {
    override val type: String
        get() = "task"

    override val viewManager = ViewManager.createWeakViewManager<TaskObjectView>()

    override fun copy(): ScoreObject = TaskObject(name, code.clone(), width)

    override fun writeStartCode(writer: ScWriter, offset: Double) {
        writer.appendBlock("Task") {
            val function = code.editor.result.now
            function.code(this)
            this.appendLine(".value()")
        }
        writer.appendLine(".play;")
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