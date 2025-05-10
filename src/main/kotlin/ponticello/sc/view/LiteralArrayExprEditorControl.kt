package ponticello.sc.view

import bundles.Bundle
import fxutils.styleClass
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl

class LiteralArrayExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: ponticello.sc.editor.LiteralArrayEditor,
    arguments: Bundle,
) : CompoundEditorControl(editor, arguments) {
    init {
        supportArguments(MULTILINE)
    }

    override fun build(): Layout = vertical {
        viewOrientableList(editor.elements, arguments[MULTILINE])
            .root.styleClass("compound-expr", "array")
    }
}
