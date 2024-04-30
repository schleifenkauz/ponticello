package xenakis.model

import hextant.core.editor.copy
import hextant.serial.makeRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.sc.editor.ScFunctionEditor

@Serializable
class TaskObject(
    override var name: String, val code: ScFunctionEditor,
    override var start: Double, override var duration: Double,
    override var y: Double, override var height: Double,
    override val controls: List<ParameterControl>
) : ScoreObject() {
    override val color: Color? get() = null

    init {
        code.makeRoot()
    }

    override fun initialize(project: XenakisProject) {

    }

    override fun clone(newName: String): ScoreObject =
        TaskObject(name, code.copy(), start, duration, y, height, controls.toMutableList())

    override fun writeStartCode(writer: ScWriter, offset: Double) {
        writer.appendBlock("Task") {
            val function = code.result.now
            function.code(this)
            this.appendLine(".value()")
        }
        writer.appendLine(".play;")
    }

    override fun writeStopCode(writer: ScWriter) {}
}