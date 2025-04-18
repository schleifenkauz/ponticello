package xenakis.model.score

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.writeCode
import xenakis.model.Settings
import xenakis.model.flow.ScoreObjectInfo
import xenakis.sc.editor.CodeBlockEditor

@Serializable
class TaskObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val code: EditorRoot<@Contextual CodeBlockEditor>,
) : ScoreObject() {
    override val type: String
        get() = "task"

    override val superColliderPrefix: String get() = "~task"

    override val canResize: Boolean
        get() = false

    override fun doClone(newName: String): ScoreObject = TaskObject(reactiveVariable(newName), code.clone(context))

    override fun initialize(context: Context) {
        super.initialize(context)
        code.initialize(context)
    }

    override fun writeCode(info: ScoreObjectInfo): String = writeCode {
        val name = superColliderName(info.suffix)
        appendBlock("$name = Task", endLine = false) {
            +"${context[Settings].serverLatency.now}.wait"
            val block = code.editor.result.now
            block.writeCode(writer, context)
        }
        appendLine(".play;")
    }
}