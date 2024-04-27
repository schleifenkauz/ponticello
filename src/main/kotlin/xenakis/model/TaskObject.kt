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
    override val color get() = Color.BLACK

    init {
        code.makeRoot()
    }

    override fun initialize(project: XenakisProject) {

    }

    override fun clone(newName: String): ScoreObject =
        TaskObject(name, code.copy(), start, duration, y, height, controls.toMutableList())

    override fun ScWriter.writeStartCode(offset: Double) = appendBlock("fork") {
        val function = code.result.now
        function.code(this)
        appendLine(".value()")
    }

    override fun writeStopCode(writer: ScWriter) {}
}