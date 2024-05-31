package xenakis.model

import hextant.core.editor.ListenerManager
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

class TaskObject(name: String, val code: EditorRoot<ScFunctionEditor>, var width: Double) : RegularScoreObject(name) {
    override val type: String
        get() = "task"

    override val viewManager = ListenerManager.createWeakListenerManager<TaskObjectView>()

    override fun copy(): ScoreObject = TaskObject(name.now, code.clone(), width)

    override fun writeStartCode(writer: ScWriter, offset: Double, suffixGenerator: SuffixGenerator) {
        val name = "~task${name.now}${suffixGenerator.generateSuffix(this)}"
        writer.appendBlock("$name = Task") {
            val function = code.editor.result.now
            function.code(this)
            this.appendLine(".value()")
        }
        writer.appendLine(".play;")
    }

    override fun writeStopCode(writer: ScWriter, suffixGenerator: SuffixGenerator) {
        val name = "~task${name.now}${suffixGenerator.getSuffix(this)}"
        writer.append("$name.stop;")
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