package xenakis.sc.view

import bundles.Bundle
import bundles.set
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ListEditorControl
import hextant.core.view.ListEditorControl.Orientation
import hextant.fx.view

class TupleExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.TupleExprEditor,
    args: Bundle
) : CompoundEditorControl(editor, args) {
    override fun build(): Layout = vertical {
        styleClass("compound-expr", "tuple")
        view(editor.elements) {
            if (arguments[MULTILINE]) set(ListEditorControl.ORIENTATION, Orientation.Vertical)
            else set(ListEditorControl.ORIENTATION, Orientation.Horizontal)
        }
    }
}