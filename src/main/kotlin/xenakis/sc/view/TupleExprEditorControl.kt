package xenakis.sc.view

import bundles.Bundle
import bundles.withDefault
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl

class TupleExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.TupleExprEditor,
    args: Bundle
) : CompoundEditorControl(editor, args.withDefault(MULTILINE)) {
    override fun build(): Layout = vertical {
        styleClass("compound-expr", "tuple")
        viewOrientableList(editor.elements, arguments[MULTILINE])
    }
}