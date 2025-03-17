package xenakis.sc.editor

import fxutils.prompt.showSelectorDialog
import hextant.command.Command
import hextant.command.meta.ProvideCommand
import hextant.context.withoutUndo
import hextant.core.editor.ConfiguredExpander
import hextant.core.editor.ExpanderConfig
import hextant.core.editor.makeUndoableEdit
import hextant.core.editor.snapshot
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.model.obj.VSTPluginObject
import xenakis.sc.*

class ScExprExpander() : ConfiguredExpander<ScExpr, ScExprEditor<*>>(), ScExprEditor<ScExpr> {
    constructor(text: String) : this() {
        setInitialText(text)
    }

    constructor(editor: ScExprEditor<*>) : this() {
        setInitialContent(editor)
    }

    init {

    }

    override fun defaultResult(): ScExpr = EmptyExpr

    override fun autoExpand(text: String): Boolean {
        val editor = when {
            text in Operator.map -> OperatorExprEditor(operator = OperatorEditor(text))
            text.endsWith(".") && text.dropLast(1).toIntOrNull() == null ->
                MessageSendEditor(ScExprExpander(text.dropLast(1)))

            text == "=" -> AssignmentEditor()
            text == "." -> MessageSendEditor()
            text == ":" -> NamedExprEditor()

            text.endsWith("=") && Identifier.isValid(text.dropLast(1)) ->
                AssignmentEditor(IdentifierEditor(text.dropLast(1)))

            text.endsWith(":") && Identifier.isValid(text.dropLast(1)) ->
                NamedExprEditor(IdentifierEditor(text.dropLast(1)))

            text.endsWith("(") && Identifier.isValid(text.dropLast(1)) ->
                NewObjectEditor(IdentifierEditor(text.dropLast(1)))

            text == "\\" -> SymbolLiteralEditor()
            text == "'" -> StringLiteralEditor()
            text == "{" -> ScFunctionEditor()
            text == "(" -> CodeBlockEditor()
            else -> null
        }
        return if (editor != null) {
            autoExpandTo(editor)
        } else super.autoExpand(text)
    }

    override fun onExpansion(editor: ScExprEditor<*>) {
        when {
            editor is AssignmentEditor && editor.variable.text.now.isNotEmpty() ->
                editor.expression.notifyViews { focus() }

            editor is MessageSendEditor && editor.receiver.result.now != EmptyExpr ->
                editor.method.notifyViews { focus() }

            editor is NamedExprEditor && editor.name.text.now != "" ->
                editor.value.notifyViews { focus() }

            editor is NewObjectEditor && editor.className.text.now != "" ->
                editor.arguments.notifyViews { focus() }
        }
    }

    override fun compile(token: String): ScExpr {
        Literal.compile(token).takeIf { it !is Invalid }?.let { return it }
        if (Identifier.isValid(token)) return Identifier(token)
        return when {
            token == "" -> EmptyExpr
            else -> UnrecognizedToken(token)
        }
    }

    @ProvideCommand(shortName = "assign", type = Command.Type.SingleReceiver)
    fun assignToVariable() = makeUndoableEdit("Wrap in assignment") {
        val value = withoutUndo { snapshot() }
        val variable = IdentifierEditor()
        val assignment = AssignmentEditor(variable, value)
        expand(assignment)
        variable.notifyViews { focus() }
    }

    @ProvideCommand(shortName = "name", type = Command.Type.SingleReceiver)
    fun nameValue() = makeUndoableEdit("Wrap in named value") {
        val value = withoutUndo { snapshot() }
        val variable = IdentifierEditor()
        val named = NamedExprEditor(variable, value)
        expand(named)
        variable.notifyViews { focus() }
    }

    @ProvideCommand(shortName = "send", type = Command.Type.SingleReceiver)
    fun callMethod() = makeUndoableEdit("Wrap in method call") {
        val receiver = withoutUndo { snapshot() }
        val send = MessageSendEditor(receiver)
        expand(send)
        send.method.notifyViews { focus() }
    }

    companion object {
        val config = ExpanderConfig<ScExprEditor<*>>(fallback = LiteralExpander.config).apply {
            "at" expand { AccessKeyEditor() }
            "array" expand { ArrayExprEditor() }
            "if" expand { IfExprEditor() }
            "while" expand { WhileExprEditor() }
            "assign" expand { AssignmentEditor() }
            "eq" expand { OperatorExprEditor(operator = OperatorEditor("==")) }
            "concat" expand { OperatorExprEditor(operator = OperatorEditor("++")) }
            "tuple" expand { TupleExprEditor() }
            "named" expand { NamedExprEditor() }
            "block" expand { CodeBlockEditor() }
            "function" expand { ScFunctionEditor() }
            "new" expand { NewObjectEditor() }
            "bus" expand { BusSelector() }
            "buffer" expand { BufferSelector() }
            "group" expand { GroupSelector() }
            "plugin" expand { ctx ->
                val availablePlugins = VSTPluginObject.availablePlugins(ctx).toList()
                val pluginName = showSelectorDialog("Plugin", availablePlugins, null, anchor = null)
                    ?: return@expand null
                VSTPluginEditor(pluginName)
            }
            "synth" expand { AdhocSynthEditor() }
            "in" expand { InExprEditor() }
            "out" expand { OutExprEditor() }
        }
    }
}