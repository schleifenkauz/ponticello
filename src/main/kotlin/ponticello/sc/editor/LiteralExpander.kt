package ponticello.sc.editor

import hextant.core.editor.ConfiguredExpander
import hextant.core.editor.ExpanderConfig
import ponticello.sc.Literal

class LiteralExpander : ConfiguredExpander<Literal, LiteralEditor<*>>() {
    init {
        configure(config)
    }

    override fun compile(token: String): Literal = Literal.compile(token)

    companion object {
        val config = ExpanderConfig<LiteralEditor<*>>().apply {
            "#" expand { LiteralArrayEditor() }
        }
    }
}