package ponticello.ui.launcher

import bundles.set
import fxutils.prompt.PredicateTextPrompt
import fxutils.shortcut
import hextant.command.Command
import hextant.context.ControlFactory
import hextant.context.EditorControlGroup
import hextant.context.SelectionDistributor
import hextant.context.compoundEdit
import hextant.core.editor.*
import hextant.core.view.EditorControl
import hextant.core.view.ListEditorControl
import hextant.plugins.*
import hextant.serial.PropertyAccessor
import ponticello.impl.Logger
import ponticello.impl.one
import ponticello.impl.randomColor
import ponticello.model.obj.CustomizableSynthDefObject
import ponticello.model.obj.ParameterDefObject
import ponticello.model.registry.InstrumentRegistry
import ponticello.model.score.controls.AttackReleaseControl
import ponticello.sc.DecimalLiteral
import ponticello.sc.Identifier
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.sc.editor.*
import ponticello.sc.view.*
import ponticello.sc.view.ScFunctionEditorControl.Companion.SINGLE_LINE_FUNCTION
import ponticello.ui.actions.addAllNamedArguments
import ponticello.ui.actions.showParameterInfo
import ponticello.ui.impl.showDialog
import ponticello.ui.launcher.PonticelloHextantPlugin.multilineCommand
import ponticello.ui.launcher.PonticelloHextantPlugin.singleLineCommand
import reaktive.value.now

object PonticelloHextantPlugin : PluginInitializer({
    registerControlFactory(::AdhocSynthEditorControl)
    registerControlFactory(::ArrayExprEditorControl)
    registerControlFactory(::CheckBoxControl)
    registerControlFactory(::CodeBlockEditorControl)
    registerControlFactory(::ColorPickerControl)
    registerControlFactory(::IntSpinnerControl)
    registerControlFactory(::LiteralArrayExprEditorControl)
    registerControlFactory(::MessageSendEditorControl)
    registerControlFactory(::RawScExprEditorControl)
    registerControlFactory(::ScFunctionEditorControl)
    registerControlFactory(::SynthExprEditorControl)
    registerControlFactory(::TupleExprEditorControl)

    registerControlFactories()

    stylesheet("ponticello/ui/style/flow.css")
    stylesheet("ponticello/ui/style/controls.css")
    stylesheet("ponticello/ui/style/tool-panes.css")
    stylesheet("ponticello/ui/style/launcher.css")
    stylesheet("ponticello/ui/style/toolbar.css")
    stylesheet("ponticello/ui/style/knob.css")
    stylesheet("ponticello/ui/style/general.css")
    stylesheet("ponticello/ui/style/score.css")
    stylesheet("ponticello/ui/style/launcher-grid.css")
    stylesheet("ponticello/ui/style/syntax.css")
    stylesheet("ponticello/ui/style/mixer.css")

    stylesheet("fxutils/style.css")
    stylesheet("fxutils/prompts.css")
    stylesheet("fxutils/list-popup.css")
    stylesheet("fxutils/controls.css")
    stylesheet("fxutils/icon-buttons.css")
    stylesheet("fxutils/buttons.css")
    stylesheet("fxutils/general.css")

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
        applicableIf { ctrl -> ctrl.canBeSingleLine.now && ctrl.arguments[SINGLE_LINE_FUNCTION] }
        executing { ctrl ->
            ctrl.arguments[SINGLE_LINE_FUNCTION] = false
        }
    }
    registerCommand<ScFunctionEditorControl, Unit> {
        shortName = "single line"
        name = "Convert to single-line function"
        applicableIf { ctrl -> ctrl.canBeSingleLine.now && !ctrl.arguments[SINGLE_LINE_FUNCTION] }
        executing { ctrl ->
            ctrl.arguments[SINGLE_LINE_FUNCTION] = true
        }
    }

    registerCommand<MessageSendEditorControl, Unit> {
        shortName = "toggle show 'new'-keyword"
        name = "Toggle the visibility of the method name 'new'"
        defaultShortcut("Alt+N")
        applicableIf { ctrl -> ctrl.usesMethodNew.now }
        executing { ctrl ->
            val hide = !ctrl.arguments[HIDE_NEW_KEYWORD]
            if (ctrl.getChild(MessageSendEditor::method)?.isFocusWithin == true) {
                ctrl.getChild(MessageSendEditor::arguments)?.receiveFocus()
            }
            ctrl.arguments[HIDE_NEW_KEYWORD] = hide
        }
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
        else when (ctrl.editorParent) {
            is MessageSendEditorControl,
            is ArrayExprEditorControl,
            is LiteralArrayExprEditorControl,
            is TupleExprEditorControl,
            is SynthExprEditorControl,
                -> ctrl.editorParent

            else -> null
        }
    }

    registerCommand<ScExprEditor<*>, Unit> {
        shortName = "unwrap"
        name = "Unwrap and replace parent"
        defaultShortcut = "Alt+X".shortcut
        applicableIf { editor -> editor.parent?.expander is ScExprExpander }
        executing { editor ->
            val parent = editor.parent ?: return@executing
            val newEditor = editor.snapshot()
            parent.replaceWith(newEditor, editDescription = "Unwrap expression")
            val view = editor.context[EditorControlGroup].getViewOf(newEditor)
            view.select()
        }
    }

    registerCommand<ScExprEditor<*>, Unit> {
        shortName = "toggle comment"
        defaultShortcut = "Alt+D".shortcut
        type = Command.Type.MultipleReceivers
        name = "Toggle comment"
        applicableIf { ed -> ed.expander is ScExprExpander }
        executing { editor ->
            val exp = editor.expander as? ScExprExpander ?: return@executing
            exp.toggleDisabled()
        }
    }

    registerCommand<IdentifierEditor, Unit> {
        shortName = "add-synth-args"
        name = "Add all relevant arguments to Synth"
        applicableIf { ed ->
            val synthDefName = ed.result.now.text
            ed.isSubEditor(SynthExprEditor::synthDef) && ed.context[InstrumentRegistry].has(synthDefName)
        }
        executing { ed ->
            val synthDefName = ed.result.now.text
            val synthDef = ed.context[InstrumentRegistry].get(synthDefName)
            val expr = ed.parent as SynthExprEditor
            val existingArguments = expr.arguments.result.now.map { arg -> arg.name.text } + "duration"
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

    registerCommand<ScExprEditor<*>, Unit> {
        shortName = "show-parameter"
        name = "Show parameter info"
        defaultShortcut = "Alt+P".shortcut
        applicableIf { editor ->
            val parent = editor.parent
            parent is ScExprListEditor && parent.isSubEditor(MessageSendEditor::arguments)
        }
        executing { editor ->
            val messageSend = editor.getParent<MessageSendEditor>() ?: return@executing
            showParameterInfo(editor, messageSend) { argumentString ->
                val index = messageSend.arguments.editors.now.indexOf(editor)
                argumentString.split(", ")[index]
            }
        }
    }

    registerCommand<ScExprEditor<*>, Unit> {
        shortName = "show-parameters"
        name = "Show parameters"
        defaultShortcut = "Alt+Shift+P".shortcut
        applicableIf { editor -> editor.getParent<MessageSendEditor>() != null }
        executing { editor ->
            val messageSend = editor.getParent<MessageSendEditor>() ?: return@executing
            showParameterInfo(editor, messageSend)
        }
    }

    registerCommand<MessageSendEditor, Unit> {
        shortName = "add-all-named-arguments"
        name = "Add named arguments for all parameters"
        executing { editor ->
            addAllNamedArguments(editor)
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
                Warp.Linear, DecimalLiteral(one), DecimalLiteral(AttackReleaseControl.DEFAULT), randomColor()
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
            applicableIf { ctrl -> !ctrl.arguments[MULTILINE] }
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
            applicableIf { ctrl -> ctrl.arguments[MULTILINE] }
            executing { ctrl, _ ->
                val selectedBefore = ctrl.context[SelectionDistributor].focusedView.now
                ctrl.arguments[MULTILINE] = false
                selectedBefore?.select()
            }
        }
}