package xenakis.model

import hextant.context.Context
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.model.obj.AbstractContextualObject
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.code
import xenakis.sc.editor.CodeBlockEditor

@Serializable
data class SetupCode(
    val serverSetup: EditorRoot<@Contextual CodeBlockEditor>,
    val serverTree: EditorRoot<@Contextual CodeBlockEditor>,
) : AbstractContextualObject() {
    override fun initialize(context: Context) {
        super.initialize(context)
        serverSetup.initialize(context)
        serverTree.initialize(context)
        val client = this.context[SuperColliderClient]
        client.onTreeCleared {
            val treeSetup = serverTree.editor.result.now
            client.run(treeSetup.code(this.context))
        }
        client.onServerBooted {
            val serverSetup = serverSetup.editor.result.now
            client.run(serverSetup.code(context))
        }
    }


    companion object {
        fun default() = SetupCode(
            EditorRoot(CodeBlockEditor().defaultState()),
            EditorRoot(CodeBlockEditor().defaultState())
        )
    }
}