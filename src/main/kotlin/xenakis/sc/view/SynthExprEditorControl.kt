package xenakis.sc.view

import bundles.Bundle
import bundles.set
import bundles.withDefault
import fxutils.centerChildren
import fxutils.styleClass
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ListEditorControl
import hextant.core.view.ListEditorControl.Companion.ADD_WITH_COMMA
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation
import hextant.core.view.ListEditorControl.SeparatorCell
import hextant.fx.view
import javafx.scene.layout.HBox

class SynthExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.SynthExprEditor,
    arguments: Bundle,
) : CompoundEditorControl(editor, arguments.withDefault(MULTILINE, false)) {
    override fun build(): Layout = vertical {
        styleClass("compound-expr", "synth-expr")
        horizontal {
            keyword("Synth")
            space()
            view(editor.synthDef)
            space()
            keyword("[")
            if (!arguments[MULTILINE]) {
                view(editor.arguments, cached = false) {
                    set(ORIENTATION, Orientation.Horizontal)
                    set(CELL_FACTORY) { SeparatorCell(", ").also { it.root.centerChildren() } }
                    set(ADD_WITH_COMMA, true)
                }.root.styleClass("arguments").centerChildren()
                keyword("]")
            }
        }
        if (arguments[MULTILINE]) {
            indented {
                view(editor.arguments, cached = false) {
                    set(ORIENTATION, Orientation.Vertical)
                    set(CELL_FACTORY) { ListEditorControl.DefaultCell { item -> HBox(item) } }
                }
            }
            keyword("]")
        }
    }
}