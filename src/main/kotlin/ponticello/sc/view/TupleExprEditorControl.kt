package ponticello.sc.view

import bundles.Bundle
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl

class TupleExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: ponticello.sc.editor.TupleExprEditor,
    args: Bundle
) : CompoundEditorControl(editor, args) {
    init {
        supportArguments(MULTILINE)
    }

    override fun build(): Layout = vertical {
        styleClass("compound-expr", "tuple")
        viewOrientableList(editor.elements, arguments[MULTILINE])
    }
}