package xenakis.sc.view

import bundles.Bundle
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.EditorControl
import javafx.scene.control.Label
import xenakis.sc.editor.RawScExprEditor
import xenakis.ui.label

class RawScExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: RawScExprEditor, arguments: Bundle
) : EditorControl<Label>(editor, arguments) {
    override fun createDefaultRoot(): Label = label(editor.text)
}