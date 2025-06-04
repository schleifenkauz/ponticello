package ponticello.model.score

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.writeCode
import ponticello.model.Settings
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.ParameterDefObject
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.editor.CodeBlockEditor
import reaktive.value.now

@Serializable
class TaskObject(
    val code: EditorRoot<@Contextual CodeBlockEditor>,
) : ScoreObject() {
    override val type: String
        get() = "task"

    override val superColliderPrefix: String get() = "~task"

    override val canResize: Boolean
        get() = false

    override fun doClone(): ScoreObject = TaskObject(code.clone(context))

    override fun initialize(context: Context) {
        super.initialize(context)
        code.initialize(context)
    }

    override fun writeCode(
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
        latency: Decimal,
        extraArguments: Map<ParameterDefObject, ParameterControl>
    ): String = writeCode {
        val name = "~task_$uniqueName"
        appendBlock("$name = Task", endLine = false) {
            +"${context[Settings].serverLatency.now}.wait"
            val block = code.editor.result.now
            block.writeCode(writer, context)
            +"$name = nil"
        }
        appendLine(".play;")
    }
}