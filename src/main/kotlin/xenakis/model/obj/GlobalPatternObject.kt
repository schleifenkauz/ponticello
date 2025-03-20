package xenakis.model.obj

import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
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
        get() = TODO("Not yet implemented")

    override fun ScWriter.createObject() {
        TODO("Not yet implemented")
    }
}