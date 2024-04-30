package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.ConfiguredExpander
import hextant.core.editor.ExpanderConfig
import xenakis.sc.Literal

class LiteralExpander(context: Context) : ConfiguredExpander<Literal, LiteralEditor<*>>(config, context) {
    override fun compile(token: String): Literal = Literal.compile(token)

    companion object {
        val config = ExpanderConfig<LiteralEditor<*>>().apply {
            "#" expand { ctx -> LiteralArrayEditor(ctx) }
        }
    }
}