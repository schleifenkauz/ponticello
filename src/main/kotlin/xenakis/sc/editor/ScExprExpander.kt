package xenakis.sc.editor

import fxutils.prompt.showSelectorDialog
import hextant.command.Command
import hextant.command.meta.ProvideCommand
import hextant.context.withoutUndo
import hextant.core.editor.*
import reaktive.value.now
import xenakis.model.obj.VSTPluginObject
import xenakis.sc.*

class ScExprExpander() : ConfiguredExpander<ScExpr, ScExprEditor<*>>(), ScExprEditor<ScExpr> {
    init {
        configure(config)
    }

    constructor(text: String) : this() {
        setInitialText(text)
    }

    constructor(editor: ScExprEditor<*>) : this() {
        setInitialContent(editor)
    }

    override fun defaultResult(): ScExpr = EmptyExpr

    override fun autoExpand(text: String): Boolean {
        val editor = when {
            text in Operator.map -> OperatorExprEditor(
                operator = OperatorEditor(text),
                left = ScExprExpander().defaultState(),
                right = ScExprExpander().defaultState()
            )

            text.endsWith(".") && text.dropLast(1).toIntOrNull() == null ->
                MessageSendEditor(
                    ScExprExpander(text.dropLast(1)),
                    IdentifierEditor().defaultState(),
                    ScExprListEditor().defaultState()
                )

            text == "=" -> AssignmentEditor().defaultState()
            text == "." -> MessageSendEditor().defaultState()
            text == ":" -> NamedExprEditor().defaultState()

            text.endsWith("=") && Identifier.isValid(text.dropLast(1)) -> AssignmentEditor(
                IdentifierEditor(text.dropLast(1)),
                ScExprExpander().defaultState()
            )

            text.endsWith(":") && Identifier.isValid(text.dropLast(1)) -> NamedExprEditor(
                IdentifierEditor(text.dropLast(1)),
                ScExprExpander().defaultState()
            )

            text == "new" -> MessageSendEditor(
                receiver = ScExprExpander(IdentifierEditor("")),
                method = IdentifierEditor("new"),
                arguments = ScExprListEditor().defaultState()
            )

            text == "\\" -> SymbolLiteralEditor().defaultState()
            text == "\"" -> StringLiteralEditor().defaultState()
            text == "{" -> ScFunctionEditor().defaultState()
            text == "(" -> CodeBlockEditor().defaultState()
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
            "at" expand { AccessKeyEditor().defaultState() }
            "array" expand { ArrayExprEditor().defaultState() }
            "if" expand { IfExprEditor().defaultState() }
            "while" expand { WhileExprEditor().defaultState() }
            "loop" expand { LoopExprEditor().defaultState() }
            "assign" expand { AssignmentEditor().defaultState() }
            "eq" expand {
                OperatorExprEditor(
                    ScExprExpander().defaultState(),
                    operator = OperatorEditor("=="),
                    ScExprExpander().defaultState()
                )
            }
            "concat" expand {
                OperatorExprEditor(
                    operator = OperatorEditor("++"),
                    left = ScExprExpander().defaultState(),
                    right = ScExprExpander().defaultState()
                )
            }
            "exp" expand {
                OperatorExprEditor(
                    operator = OperatorEditor("**"),
                    left = ScExprExpander().defaultState(),
                    right = ScExprExpander().defaultState()
                )
            }
            "tuple" expand { TupleExprEditor().defaultState() }
            "named" expand { NamedExprEditor().defaultState() }
            "block" expand { CodeBlockEditor().defaultState() }
            "function" expand { ScFunctionEditor().defaultState() }
            "bus" expand { BusSelector().defaultState() }
            "buffer" expand { BufferSelector().defaultState() }
            "group" expand { GroupSelector().defaultState() }
            "plugin" expand { ctx ->
                val availablePlugins = VSTPluginObject.availablePlugins(ctx).toList()
                val pluginName = showSelectorDialog("Plugin", availablePlugins, null, anchor = null)
                    ?: return@expand null
                VSTPluginEditor(pluginName).defaultState()
            }
            "adhoc-synth" expand { AdhocSynthEditor().defaultState() }
            "synth" expand { SynthExprEditor().defaultState() }
            "in" expand { InExprEditor().defaultState() }
            "out" expand { OutExprEditor().defaultState() }
        }
    }
}