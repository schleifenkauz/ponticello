package ponticello.sc.view

import bundles.Bundle
import fxutils.runFXWithTimeout
import hextant.core.Editor
import hextant.core.view.CompoundEditorControl
import hextant.core.view.EditorControl
import hextant.core.view.ExpanderControl
import ponticello.sc.editor.BusExprEditor
import ponticello.sc.editor.ScExprExpander

class ExprExpanderControl(expander: ScExprExpander, args: Bundle) : ExpanderControl(expander, args) {
    override fun onExpansion(editor: Editor<*>, control: EditorControl<*>) {
        if (control.scene == null) return
        runFXWithTimeout {
            if (control is ObjectSelectorControl<*>) {
                control.showChoicePopup()
            }
            if (editor is BusExprEditor && control is CompoundEditorControl) {
                val selectorCtrl =
                    control.getSubControl(editor.busSelector) as? ObjectSelectorControl<*> ?: return@runFXWithTimeout
                selectorCtrl.showChoicePopup()
            }
        }
    }
}