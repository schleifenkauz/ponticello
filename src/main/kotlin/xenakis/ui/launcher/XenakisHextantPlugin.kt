package xenakis.ui.launcher

import bundles.set
import fxutils.prompt.PredicateTextPrompt
import hextant.context.ControlFactory
import hextant.context.SelectionDistributor
import hextant.core.editor.defaultState
import hextant.core.editor.isSubEditor
import hextant.core.view.EditorControl
import hextant.core.view.ListEditorControl
import hextant.plugins.*
import hextant.serial.PropertyAccessor
import hextant.undo.compoundEdit
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.impl.one
import xenakis.impl.randomColor
import xenakis.model.obj.CustomizableSynthDefObject
import xenakis.model.obj.ParameterDefObject
import xenakis.model.registry.SynthDefRegistry
import xenakis.sc.DecimalLiteral
import xenakis.sc.Identifier
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.editor.*
import xenakis.sc.view.*
import xenakis.sc.view.ScFunctionEditorControl.Companion.SINGLE_LINE_FUNCTION
import xenakis.ui.impl.showDialog
import xenakis.ui.launcher.XenakisHextantPlugin.multilineCommand
import xenakis.ui.launcher.XenakisHextantPlugin.singleLineCommand

object XenakisHextantPlugin : PluginInitializer({
    stylesheet("xenakis/ui/style/flow.css")
    stylesheet("xenakis/ui/style/controls.css")
    stylesheet("xenakis/ui/style/tool-panes.css")
    stylesheet("xenakis/ui/style/launcher.css")
    stylesheet("xenakis/ui/style/toolbar.css")
    stylesheet("xenakis/ui/style/knob.css")
    stylesheet("xenakis/ui/style/general.css")
    stylesheet("xenakis/ui/style/score.css")
    stylesheet("fxutils/style.css")
    stylesheet("xenakis/ui/style/prompt.css")
    stylesheet("xenakis/ui/style/syntax.css")
    on(PluginBuilder.Phase.Initialize) { ctx ->
        ctx[Aspects].implement(ControlFactory::class, ScExprExpander::class, ScExprExpanderControlFactory)
    }

    multilineCommand<MessageSendEditorControl>("arguments")
    singleLineCommand<MessageSendEditorControl>("arguments")
    multilineCommand<ArrayExprEditorControl>("elements")
    singleLineCommand<ArrayExprEditorControl>("elements")
    singleLineCommand<LiteralArrayExprEditorControl>("elements")
    multilineCommand<LiteralArrayExprEditorControl>("elements")
    singleLineCommand<TupleExprEditorControl>("elements")
    multilineCommand<TupleExprEditorControl>("elements")
    singleLineCommand<SynthExprEditorControl>("arguments")
    multilineCommand<SynthExprEditorControl>("arguments")

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

    registerCommand<MessageSendEditorControl, Unit> {
        shortName = "toggle show 'new'-keyword"
        name = "Toggle the visibility of the method name 'new'"
        defaultShortcut("Alt+N")
        applicableIf { ctrl -> ctrl.usesMethodNew.now }
        executing { ctrl -> ctrl.arguments[HIDE_NEW_KEYWORD] = !ctrl.arguments[HIDE_NEW_KEYWORD] }
    }

    commandDelegation<EditorControl<*>> { ctrl ->
        val parent = ctrl.editorParent
        when {
            parent !is MessageSendEditorControl -> null
            !parent.usesMethodNew.now -> null
            ctrl.target.accessor == PropertyAccessor(MessageSendEditor::receiver.name) -> parent
            ctrl.target.accessor == PropertyAccessor(MessageSendEditor::method.name) -> parent
            else -> parent
        }
    }

    commandDelegation<ListEditorControl> { ctrl ->
        if (ctrl.target !is ScExprListEditor) null
        when (ctrl.editorParent) {
            is MessageSendEditorControl,
            is ArrayExprEditorControl,
            is LiteralArrayExprEditorControl,
            is TupleExprEditorControl,
            is SynthExprEditorControl,
                -> ctrl.editorParent

            else -> null
        }
    }

    registerCommand<IdentifierEditor, Unit> {
        shortName = "add-synth-args"
        name = "Add all relevant arguments to Synth"
        applicableIf { ed ->
            val synthDefName = ed.result.now.text
            ed.isSubEditor(SynthExprEditor::synthDef) && ed.context[SynthDefRegistry].has(synthDefName)
        }
        executing { ed ->
            val synthDefName = ed.result.now.text
            val synthDef = ed.context[SynthDefRegistry].get(synthDefName)
            val expr = ed.parent as SynthExprEditor
            val existingArguments = expr.arguments.result.now.map { arg -> arg.name.text }
            for (param in synthDef.parameters) {
                val name = param.name.now
                if (name in existingArguments) continue
                expr.arguments.addLast(
                    NamedExprEditor(
                        IdentifierEditor(name),
                        ScExprExpander().defaultState()
                    )
                )
            }
        }
    }

    registerCommand<ScExprExpander, Unit> {
        shortName = "extract-param"
        name = "Extract parameter"
        applicableIf { ed ->
            ed.result.now is DecimalLiteral && ed.context.hasProperty(CustomizableSynthDefObject.editedSynthDef)
        }
        executing { editor ->
            val name = PredicateTextPrompt("Parameter name", "") { name -> Identifier.isValid(name) }
                .showDialog(editor.context) ?: return@executing
            val def = editor.context[CustomizableSynthDefObject.editedSynthDef]
            val parameters = def.parameters
            val defaultValue = editor.result.now
            if (defaultValue !is DecimalLiteral) {
                Logger.error("Could not extract parameter default value.")
                return@executing
            }
            val spec = NumericalControlSpec(
                defaultValue, defaultValue, defaultValue,
                Warp.Linear, DecimalLiteral(one), randomColor()
            )
            val param = ParameterDefObject(name, spec)
            editor.context.compoundEdit("Extract parameter") {
                parameters.add(param)
                editor.setText(name)
            }
        }
    }
}) {
    private inline fun <reified C : EditorControl<*>> PluginBuilder.multilineCommand(itemType: String) =
        registerCommand<C, Unit> {
            shortName = "multiline"
            name = "Put $itemType on multiple lines"
            defaultShortcut("Alt+L")
            applicableIf { ctrl -> ctrl.arguments[MULTILINE] == false }
            executing { ctrl, _ ->
                val selectedBefore = ctrl.context[SelectionDistributor].focusedView.now
                ctrl.arguments[MULTILINE] = true
                selectedBefore?.select()
            }
        }

    private inline fun <reified C : EditorControl<*>> PluginBuilder.singleLineCommand(itemType: String) =
        registerCommand<C, Unit> {
            shortName = "singleLine"
            name = "Put $itemType on single line"
            defaultShortcut("Alt+L")
            applicableIf { ctrl -> ctrl.arguments[MULTILINE] == true }
            executing { ctrl, _ ->
                val selectedBefore = ctrl.context[SelectionDistributor].focusedView.now
                ctrl.arguments[MULTILINE] = false
                selectedBefore?.select()
            }
        }
}