package xenakis.sc.view

import bundles.Bundle
import bundles.set
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ListEditorControl
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation
import hextant.core.view.ListEditorControl.SeparatorCell
import hextant.fx.view
import javafx.scene.layout.HBox
import reaktive.collection.binding.isNotEmpty
import reaktive.value.now
import xenakis.ui.centerChildrenVertically
import xenakis.ui.styleClass

class NewObjectEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.NewObjectEditor,
    arguments: Bundle
) : CompoundEditorControl(editor, arguments) {
    private val anyArguments = editor.arguments.editors.isNotEmpty()

    init {
        triggerLayoutOnChange(anyArguments)
    }

    override fun build(): Layout = vertical {
        styleClass("compound-expr", "new-object")
        horizontal {
            space()
            view(editor.className)
            if (!arguments[MULTILINE] || !anyArguments.now) {
                view(editor.arguments) {
                    set(ORIENTATION, Orientation.Horizontal)
                    set(CELL_FACTORY) { SeparatorCell(" ").also { it.root.centerChildrenVertically() } }
                }.root.styleClass("arguments").centerChildrenVertically()
            }
        }
        if (arguments[MULTILINE] && anyArguments.now) {
            indented {
                view(editor.arguments) {
                    set(ORIENTATION, Orientation.Vertical)
                    set(CELL_FACTORY) { ListEditorControl.DefaultCell { item -> HBox(item) } }
                }
            }
        }
    }
}
