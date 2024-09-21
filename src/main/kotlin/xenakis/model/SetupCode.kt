package xenakis.model

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Serializable
import xenakis.sc.editor.CodeBlockEditor

@Serializable
data class SetupCode(
    val serverSetup: EditorRoot<CodeBlockEditor>,
    val serverTree: EditorRoot<CodeBlockEditor>,
) : XenakisProject.ProjectComponent {
    override val componentName: String
        get() = "setup_code"

    companion object {
        fun default(context: Context) =
            SetupCode(EditorRoot.create(CodeBlockEditor(context)), EditorRoot.create(CodeBlockEditor(context)))
    }
}