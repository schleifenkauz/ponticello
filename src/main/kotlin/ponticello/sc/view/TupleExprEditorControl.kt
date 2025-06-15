package ponticello.sc.view

import bundles.Bundle
import hextant.core.view.CompoundEditorControl

class TupleExprEditorControl (
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