package ponticello.sc.view

import bundles.Bundle
import hextant.core.view.CompoundEditorControl

class SynthExprEditorControl (
    private val editor: ponticello.sc.editor.SynthExprEditor,
    arguments: Bundle,
) : CompoundEditorControl(editor, arguments) {
    init {
        supportArguments(MULTILINE)
    }

    override fun build(): Layout = vertical {
        styleClass("compound-expr", "synth-expr")
        horizontal {
            keyword("Synth")
            space()
            view(editor.synthDef)
            space()
            operator("[")
            if (!arguments[MULTILINE]) {
                viewHorizontal(editor.arguments)
                operator("]")
            }
        }
        if (arguments[MULTILINE]) {
            indented {
                viewVertical(editor.arguments)
            }
            operator("]")
        }
    }

}