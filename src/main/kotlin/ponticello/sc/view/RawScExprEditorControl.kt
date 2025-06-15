package ponticello.sc.view

import bundles.Bundle
import fxutils.label
import hextant.core.view.EditorControl
import javafx.scene.control.Label
import ponticello.sc.editor.RawScExprEditor

class RawScExprEditorControl (
    private val editor: RawScExprEditor, arguments: Bundle
) : EditorControl<Label>(editor, arguments) {
    override fun createDefaultRoot(): Label = label(editor.text)
}