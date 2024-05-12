package xenakis.sc.editor

import hextant.command.Command
import hextant.command.meta.ProvideCommand
import hextant.core.Editor
import reaktive.value.now
import xenakis.impl.UDPSuperColliderClient
import xenakis.sc.ScExpr
import xenakis.sc.code

interface ScExprEditor<out E : ScExpr> : Editor<E> {
    fun canEvaluate() = result.now.isValid && context.hasProperty(UDPSuperColliderClient)

    @ProvideCommand(
        defaultShortcut = "Ctrl?+E",
        description = "Evaluate the selected expression",
        applicableIf = "canEvaluate",
        type = Command.Type.SingleReceiver
    )
    fun eval() {
        val client = context[UDPSuperColliderClient]
        client.postAsync(result.now.code)
    }
}