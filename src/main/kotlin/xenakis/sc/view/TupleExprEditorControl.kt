package xenakis.sc.view

import bundles.Bundle
import bundles.set
import bundles.withDefault
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ListEditorControl
import hextant.core.view.ListEditorControl.Companion.ADD_WITH_COMMA
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation
import hextant.fx.view

class TupleExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.TupleExprEditor,
    args: Bundle
) : CompoundEditorControl(editor, args.withDefault(MULTILINE)) {
    override fun build(): Layout = vertical {
        styleClass("compound-expr", "tuple")
        view(editor.elements) {
            if (arguments[MULTILINE]) set(ORIENTATION, Orientation.Vertical)
            else {
                set(ORIENTATION, Orientation.Horizontal)
                set(CELL_FACTORY) { ListEditorControl.SeparatorCell(", ") }
                set(ADD_WITH_COMMA, true)
            }
        }
    }
}