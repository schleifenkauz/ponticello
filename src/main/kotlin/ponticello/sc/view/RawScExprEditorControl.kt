package ponticello.sc.view

import bundles.Bundle
import fxutils.label
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.EditorControl
import javafx.scene.control.Label
import ponticello.sc.editor.RawScExprEditor

class RawScExprEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: RawScExprEditor, arguments: Bundle
) : EditorControl<Label>(editor, arguments) {
    override fun createDefaultRoot(): Label = label(editor.text)
}