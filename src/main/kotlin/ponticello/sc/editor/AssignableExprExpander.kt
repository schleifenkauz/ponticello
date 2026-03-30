package ponticello.sc.editor

import ponticello.sc.AssignableScExpr
import ponticello.sc.EmptyExpr
import ponticello.sc.Identifier
import ponticello.sc.UnrecognizedToken

class AssignableExprExpander() : AbstractScExprExpander<AssignableScExpr>(), ScExprEditor<AssignableScExpr> {
    constructor(text: String) : this() {
        setInitialText(text)
    }

    override fun defaultResult(): AssignableScExpr = EmptyExpr

    override fun compile(token: String): AssignableScExpr = when {
        Identifier.isValid(token) -> Identifier(token)
        token == "" -> EmptyExpr
        else -> UnrecognizedToken(token)
    }
}