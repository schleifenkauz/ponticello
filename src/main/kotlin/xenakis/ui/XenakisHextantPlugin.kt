package xenakis.ui

import bundles.set
import hextant.context.ControlFactory
import hextant.context.SelectionDistributor
import hextant.core.view.EditorControl
import hextant.plugins.*
import reaktive.value.now
import xenakis.sc.editor.ScExprExpander
import xenakis.sc.view.*
import xenakis.sc.view.ScFunctionEditorControl.Companion.SINGLE_LINE_FUNCTION
import xenakis.ui.XenakisHextantPlugin.multilineCommand
import xenakis.ui.XenakisHextantPlugin.singleLineCommand

object XenakisHextantPlugin : PluginInitializer({
    stylesheet("xenakis/ui/style.css")
    on(PluginBuilder.Phase.Initialize) { ctx ->
        ctx[Aspects].implement(ControlFactory::class, ScExprExpander::class, ScExprExpanderControlFactory)
    }

    multilineCommand<NewObjectEditorControl>("arguments")
    singleLineCommand<NewObjectEditorControl>("arguments")
    multilineCommand<MessageSendEditorControl>("arguments")
    singleLineCommand<MessageSendEditorControl>("arguments")
    multilineCommand<ArrayExprEditorControl>("elements")
    singleLineCommand<ArrayExprEditorControl>("elements")
    singleLineCommand<LiteralArrayExprEditorControl>("elements")
    multilineCommand<LiteralArrayExprEditorControl>("elements")
    singleLineCommand<TupleExprEditorControl>("elements")
    multilineCommand<TupleExprEditorControl>("elements")

    registerCommand<ScFunctionEditorControl, Unit> {
        shortName = "multiline"
        name = "Convert to multi-line function"
        executing { ctrl -> ctrl.arguments[SINGLE_LINE_FUNCTION] = false }
    }
    registerCommand<ScFunctionEditorControl, Unit> {
        shortName = "single line"
        name = "Convert to single-line function"
        applicableIf { ctrl -> ctrl.canBeSingleLine.now }
        executing { ctrl -> ctrl.arguments[SINGLE_LINE_FUNCTION] = true }
    }
}) {
    private inline fun <reified C : EditorControl<*>> PluginBuilder.multilineCommand(itemType: String) =
        registerCommand<C, Unit> {
            shortName = "multiline"
            name = "Put $itemType on multiple lines"
            defaultShortcut("Alt?+V")
            executing { ctrl, _ ->
                val selectedBefore = ctrl.context[SelectionDistributor].focusedView.now
                ctrl.arguments[MULTILINE] = true
                selectedBefore?.select()
            }
        }

    private inline fun <reified C : EditorControl<*>> PluginBuilder.singleLineCommand(itemType: String) =
        registerCommand<C, Unit> {
            shortName = "singleLine"
            name = "Put $itemType on single lines"
            defaultShortcut("Alt?+H")
            executing { ctrl, _ ->
                val selectedBefore = ctrl.context[SelectionDistributor].focusedView.now
                ctrl.arguments[MULTILINE] = false
                selectedBefore?.select()
            }
        }
}