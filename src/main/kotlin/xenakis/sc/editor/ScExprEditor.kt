package xenakis.sc.editor

import hextant.command.Command
import hextant.command.meta.ProvideCommand
import hextant.core.Editor
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.sc.ScExpr
import xenakis.sc.code

interface ScExprEditor<out E : ScExpr> : Editor<E> {
    fun canEvaluate() = result.now.isValid && context.hasProperty(SuperColliderClient)

    @ProvideCommand(
        defaultShortcut = "Ctrl?+E",
        description = "Evaluate the selected expression",
        applicableIf = "canEvaluate",
        type = Command.Type.SingleReceiver
    )
    fun eval() {
        val client = context[SuperColliderClient]
        client.run(result.now.code)
    }
}