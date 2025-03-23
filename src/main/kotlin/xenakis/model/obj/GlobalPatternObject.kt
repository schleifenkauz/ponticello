package xenakis.model.obj

import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.model.project.busses
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.CodeBlockEditor
import xenakis.ui.launcher.XenakisLauncher

@Serializable
class GlobalPatternObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val patternCode: EditorRoot<@Contextual CodeBlockEditor>
) : AbstractSuperColliderObject() {
    override fun canRenameTo(newName: String): Boolean = !context[XenakisLauncher.currentProject].busses.has(newName)

    override val superColliderName: String
        get() = "~pattern_${name.now}"

    override fun ScWriter.createObject() {
        val code = patternCode.editor.result.now
        append("$superColliderName = (")
        code.code(writer, context)
        appendLine(").asStream;")
    }
}