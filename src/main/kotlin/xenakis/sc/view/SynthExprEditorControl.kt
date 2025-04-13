package xenakis.sc.view

import bundles.Bundle
import bundles.withDefault
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl

class SynthExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.SynthExprEditor,
    arguments: Bundle,
) : CompoundEditorControl(editor, arguments.withDefault(MULTILINE)) {
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