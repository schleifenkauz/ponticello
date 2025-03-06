@file:Suppress("SERIALIZER_TYPE_INCOMPATIBLE")

package xenakis.sc.view

import bundles.Bundle
import bundles.createBundle
import bundles.set
import fxutils.button
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ListEditorControl
import hextant.core.view.ListEditorControl.Companion.ADD_WITH_COMMA
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.EMPTY_DISPLAY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation.Horizontal
import hextant.core.view.ListEditorControl.Orientation.Vertical
import hextant.core.view.ListEditorControl.SeparatorCell
import hextant.fx.view
import hextant.serial.SnapshotAware
import javafx.scene.layout.HBox
import kotlinx.serialization.Serializable

@Serializable(with = SnapshotAware.Serializer::class)
class CodeBlockEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    val editor: xenakis.sc.editor.CodeBlockEditor,
    arguments: Bundle
) : CompoundEditorControl(editor, arguments) {
    constructor(editor: xenakis.sc.editor.CodeBlockEditor): this(editor, createBundle())

    override fun build() =
        vertical {
            styleClass("compound-expr", "code-block")
            displayVarsAndStatements(this@vertical, editor)
        }

    companion object {
        fun displayVarsAndStatements(layout: Vertical, editor: xenakis.sc.editor.CodeBlockEditor) = with(layout) {
            horizontal {
                keyword("var")
                space()
                view(editor.variables) {
                    set(ORIENTATION, Horizontal)
                    set(CELL_FACTORY) { SeparatorCell(", ") }
                    set(ADD_WITH_COMMA, true)
                }
            }
            view(editor.statements) {
                set(ORIENTATION, Vertical)
                set(EMPTY_DISPLAY) { button("Add statement") }
                set(CELL_FACTORY) { ListEditorControl.DefaultCell { item -> HBox(item) } }
            }
        }
    }
}