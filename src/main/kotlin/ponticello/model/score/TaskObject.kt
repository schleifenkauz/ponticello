package ponticello.model.score

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.writeCode
import ponticello.model.flow.NodePlacement
import ponticello.model.instr.ParameterDefObject
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.editor.CodeBlockEditor
import reaktive.value.ReactiveVariable
import reaktive.value.now

@Serializable
@SerialName("Task")
class TaskObject(
    val code: EditorRoot<@Contextual CodeBlockEditor>,
) : ScoreObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override val type: String
        get() = "task"

    override val superColliderPrefix: String get() = "~task"

    override val canResizeHorizontally: Boolean
        get() = false

    override val canResizeVertically: Boolean
        get() = false

    override fun doClone(): ScoreObject = TaskObject(code.clone(context))

    override fun initialize(context: Context) {
        super.initialize(context)
        code.initialize(context)
    }

    override fun writeCode(
        instance: ScoreObjectInstance?,
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
        latency: Decimal,
        extraArguments: Map<ParameterDefObject, ParameterControl>
    ): String = writeCode {
        val name = "~task_$uniqueName"
        appendBlock("$name = Task", endLine = false) {
//            +"s.latency.wait"
            val block = code.editor.result.now
            block.writeCode(writer, context)
            +"$name = nil"
        }
        appendLine(".play;")
    }
}