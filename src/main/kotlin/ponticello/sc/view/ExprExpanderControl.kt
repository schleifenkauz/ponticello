package ponticello.sc.view

import bundles.Bundle
import fxutils.runAfterLayout
import hextant.completion.CompletionCollector
import hextant.completion.CompletionStrategy
import hextant.completion.CompoundCompleter
import hextant.core.Editor
import hextant.core.editor.Expander
import hextant.core.view.CompoundEditorControl
import hextant.core.view.EditorControl
import hextant.core.view.ExpanderControl
import ponticello.sc.editor.BusExprEditor
import ponticello.sc.editor.ScExprExpander

class ExprExpanderControl(expander: ScExprExpander, args: Bundle) : ExpanderControl(expander, args, Completer) {
    override fun onExpansion(editor: Editor<*>, control: EditorControl<*>) {
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

    object Completer : CompoundCompleter<Expander<*, *>, Any>({
        addCompleter(BoundVariableCompleter)
        addCompleter(BufferReferenceCompleter)
        addCompleter(GlobalPatternReferenceCompleter)
        addCompleter(BusReferenceCompleter)
        addCompleter(ScoreObjectReferenceCompleter)
        addCompleter(ScExprExpander.config.completer(CompletionStrategy.simple))
    }) {
        override suspend fun collectCompletions(
            context: Expander<*, *>, input: String, collector: CompletionCollector
        ) {
            if (input.isBlank() || input.all { ch -> ch.isDigit() }) {
                collector.finished()
            } else {
                super.collectCompletions(context, input, collector)
            }
        }
    }
}