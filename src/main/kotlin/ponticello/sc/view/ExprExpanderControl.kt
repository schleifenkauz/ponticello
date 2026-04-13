package ponticello.sc.view

import bundles.Bundle
import fxutils.runAfterLayout
import hextant.completion.CompletionCollector
import hextant.completion.CompletionStrategy
import hextant.completion.CompoundCompleter
import hextant.core.Editor
import hextant.core.editor.Expander
import hextant.core.view.EditorControl
import hextant.core.view.ExpanderControl
import ponticello.model.obj.NamedObject
import ponticello.model.registry.ObjectReference
import ponticello.sc.editor.AbstractScExprExpander
import ponticello.sc.editor.ScExprExpander
import reaktive.value.now

class ExprExpanderControl(
    expander: AbstractScExprExpander<*>, args: Bundle
) : ExpanderControl(expander, args, Completer) {
    init {
        textField.focusedProperty().addListener { _, _, focused ->
            if (!focused) {
                expander.unbindDefinition()
            }
        }
    }

    override fun displayText(text: String) {
        super.displayText(text)
        val expander = target as? AbstractScExprExpander ?: return
        if (text == "" && expander.associatedDefinition != null) {
            receiveFocus()
        }
    }

    override fun onExpansion(editor: Editor<*>, control: EditorControl<*>) {
        runAfterLayout { //TODO check if this works as well as runFXWithTimeout
            if (control is ObjectSelectorControl<*> && control.editor.result.now == ObjectReference.none<NamedObject>()) {
                control.showChoicePopup()
            }
//            if (editor is BusExprEditor && control is CompoundEditorControl) {
//                val selectorCtrl =
//                    control.getSubControl(editor.busSelector) as? ObjectSelectorControl<*> ?: return@runAfterLayout
//                selectorCtrl.showChoicePopup()
//            }
            val expander = target as? AbstractScExprExpander
            if (expander?.associatedDefinition != null) {
                control.receiveFocus()
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
        addCompleter(AudioFlowReferenceCompleter)
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