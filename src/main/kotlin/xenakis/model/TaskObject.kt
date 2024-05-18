package xenakis.model

import hextant.core.editor.ViewManager
import hextant.serial.EditorRoot
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.sc.editor.ScFunctionEditor
import xenakis.ui.TaskObjectView

class TaskObject(
    name: String, val code: EditorRoot<ScFunctionEditor>,
    var width: Double
) : ScoreObject(name) {
    override val viewManager = ViewManager.createWeakViewManager<TaskObjectView>()

    override fun clone(): ScoreObject = TaskObject(name, code.clone(), width)

    override fun writeStartCode(writer: ScWriter, offset: Double) {
        writer.appendBlock("Task") {
            val function = code.editor.result.now
            function.code(this)
            this.appendLine(".value()")
        }
        writer.appendLine(".play;")
    }
}