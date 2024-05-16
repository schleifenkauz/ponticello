package xenakis.model

import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.sc.editor.ScFunctionEditor

@Serializable
class TaskObject(
    override var name: String, val code: EditorRoot<ScFunctionEditor>,
    override var start: Double, var width: Double,
    override var y: Double, override var height: Double,
    override val controls: List<ParameterControl>,
    override var muted: Boolean = false
) : ScoreObject() {
    override val color: Color? get() = null

    override var duration: Double
        get() = 0.0
        set(value) {
            throw UnsupportedOperationException("Cannot set duration of TaskObject $name")
        }

    override fun clone(newName: String): ScoreObject =
        TaskObject(newName, code.clone(), start, duration, y, height, controls.toMutableList())

    override fun writeStartCode(writer: ScWriter, offset: Double) {
        writer.appendBlock("Task") {
            val function = code.editor.result.now
            function.code(this)
            this.appendLine(".value()")
        }
        writer.appendLine(".play;")
    }
}