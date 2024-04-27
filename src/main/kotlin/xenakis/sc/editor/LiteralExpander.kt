package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.Expander
import xenakis.sc.Literal

class LiteralExpander(context: Context) : Expander<Literal, LiteralEditor<*>>(context) {
    override fun expand(text: String): LiteralEditor<*>? = when (text) {
        "#", "[", "[]" -> LiteralArrayEditor(context)
        else -> null
    }

    override fun compile(token: String): Literal = Literal.compile(token)
}