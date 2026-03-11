package ponticello.sc.view

import bundles.Bundle
import fxutils.runAfterLayout
import hextant.core.Editor
import hextant.core.view.CompoundEditorControl
import hextant.core.view.EditorControl
import hextant.core.view.ExpanderControl
import ponticello.sc.editor.BusExprEditor
import ponticello.sc.editor.ScExprExpander

class ExprExpanderControl(expander: ScExprExpander, args: Bundle) : ExpanderControl(expander, args) {
    override fun onExpansion(editor: Editor<*>, control: EditorControl<*>) {
        if (control.scene == null) return
        runAfterLayout { //TODO check if this works as well as runFXWithTimeout
            if (control is ObjectSelectorControl<*>) {
                control.showChoicePopup()
            }
            if (editor is BusExprEditor && control is CompoundEditorControl) {
                val selectorCtrl =
                    control.getSubControl(editor.busSelector) as? ObjectSelectorControl<*> ?: return@runAfterLayout
                selectorCtrl.showChoicePopup()
            }
            //just a useful example how to do things like this
//            if (editor is TransformSignalEditor) {
//                control.getChild(TransformSignalEditor::body)
//                    ?.getChild(CodeBlock::statements)
//                    ?.getChild(IndexAccessor(0))
//                    ?.receiveFocus()
//            }
        }
    }
}