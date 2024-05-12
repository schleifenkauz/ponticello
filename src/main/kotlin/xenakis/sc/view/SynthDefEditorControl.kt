package xenakis.sc.view

import bundles.Bundle
import bundles.set
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ListEditorControl
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.EMPTY_DISPLAY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation.Vertical
import hextant.fx.view
import javafx.geometry.Insets
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import xenakis.sc.editor.ParameterDefExpander
import xenakis.ui.*

class SynthDefEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.SynthDefEditor, arguments: Bundle
) : CompoundEditorControl(editor, arguments) {

    override fun build(): Layout = vertical {
        horizontal {
            keyword("color: ")
            view(editor.associatedColor)
        }
        view(editor.parameters) {
            set(ORIENTATION, Vertical)
            set(EMPTY_DISPLAY) { Button("Add parameter") }
            set(CELL_FACTORY) {
                ListEditorControl.NumberedCell { control ->
                    val space = infiniteSpace()
                    val btnDelete = Icon.Delete.button(action = "Remove parameter") {
                        val param = control.target as ParameterDefExpander
                        editor.parameters.remove(param)
                    }
                    HBox(control, space, btnDelete).alwaysHGrow()
                        .styleClass("parameter-box")
                }.also { it.root.centerChildrenVertically() }
            }
        }
        horizontal {
            view(editor.ugenGraph).padding = Insets(5.0)
        }
    }
}
