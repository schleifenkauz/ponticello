package ponticello.sc.view

import bundles.Bundle
import fxutils.centerChildren
import fxutils.styleClass
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl

class ArrayExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: ponticello.sc.editor.ArrayExprEditor,
    arguments: Bundle,
) : CompoundEditorControl(editor, arguments) {
    init {
        supportArguments(MULTILINE)
    }

    override fun build(): Layout = vertical {
        root.styleClass("compound-expr", "array")
        if (arguments[MULTILINE]) {
            horizontal { keyword("array"); operator("[") }
            indented {
                viewVertical(editor.elements)
            }
            operator("]")
        } else {
            horizontal {
                operator("[")
                viewHorizontal(editor.elements)
                operator("]")
                root.centerChildren()
            }
        }
    }
}
