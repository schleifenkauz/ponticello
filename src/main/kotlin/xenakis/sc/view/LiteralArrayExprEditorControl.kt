package xenakis.sc.view

import bundles.Bundle
import bundles.set
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ListEditorControl.Companion.ADD_WITH_COMMA
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation
import hextant.core.view.ListEditorControl.SeparatorCell
import hextant.fx.view
import javafx.scene.control.Label
import xenakis.ui.impl.centerChildren
import xenakis.ui.impl.styleClass

class LiteralArrayExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.LiteralArrayEditor,
    arguments: Bundle
) : CompoundEditorControl(editor, arguments) {
    override fun build(): Layout = vertical {
        view(editor.elements) {
            if (arguments[MULTILINE]) {
                set(ORIENTATION, Orientation.Vertical)
            } else {
                set(ORIENTATION, Orientation.Horizontal)
                set(CELL_FACTORY) { SeparatorCell(Label(" ")).also { it.root.centerChildren() } }
                set(ADD_WITH_COMMA, true)
            }
        }.root.styleClass("compound-expr", "array")
    }
}
