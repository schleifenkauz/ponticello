package xenakis.model

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import xenakis.sc.editor.CodeBlockEditor

@Serializable
data class SetupCode(
    val serverSetup: EditorRoot<@Contextual CodeBlockEditor>,
    val serverTree: EditorRoot<@Contextual CodeBlockEditor>,
) : XenakisProject.ProjectComponent {
    override val componentName: String
        get() = "setup_code"

    fun initialize(context: Context) {
        serverSetup.initialize(context)
        serverTree.initialize(context)
    }

    companion object {
        fun default() = SetupCode(
            EditorRoot(CodeBlockEditor()),
            EditorRoot(CodeBlockEditor())
        )
    }
}