package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.Expander
import xenakis.sc.*

class ScExprExpander(context: Context) : Expander<ScExpr, ScExprEditor<*>>(context), ScExprEditor<ScExpr> {
    override fun defaultResult(): ScExpr = EmptyExpr

    override fun expand(text: String): ScExprEditor<*>? = when (text) {
        "." -> MessageSendEditor(context)
        "+", "-", "*", "/", "%", "++" -> OperatorExprEditor(context, operator = OperatorEditor(context, text))
        "at" -> AccessKeyEditor(context)
        "[", "[]", "array" -> ArrayExprEditor(context)
        else -> null
    }

    override fun compile(token: String): ScExpr {
        Literal.compile(token).takeIf { it !is Invalid }?.let { return it }
        return when {
            token == "" -> EmptyExpr
            else -> UnrecognizedToken(token)
        }
    }
}