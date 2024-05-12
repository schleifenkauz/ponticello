package xenakis.sc.view

import bundles.Bundle
import bundles.set
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation
import hextant.core.view.ListEditorControl.SeparatorCell
import hextant.fx.view
import xenakis.ui.centerChildrenVertically
import xenakis.ui.styleClass

class ArrayExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.ArrayExprEditor,
    arguments: Bundle
) : CompoundEditorControl(editor, arguments) {
    override fun build(): Layout = vertical {
        view(editor.elements) {
            if (arguments[MULTILINE]) set(ORIENTATION, Orientation.Vertical)
            else {
                set(ORIENTATION, Orientation.Horizontal)
                set(CELL_FACTORY) { SeparatorCell(" ").also { it.root.centerChildrenVertically() } }
            }
        }.root.styleClass("compound-expr", "array")
    }
}
