package xenakis.sc.editor

import hextant.command.Command
import hextant.command.meta.ProvideCommand
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.editor.ConfiguredExpander
import hextant.core.editor.ExpanderConfig
import hextant.core.editor.copy
import hextant.undo.makeUndoableEdit
import reaktive.value.now
import xenakis.sc.*

class ScExprExpander(context: Context) : ConfiguredExpander<ScExpr, ScExprEditor<*>>(config, context),
    ScExprEditor<ScExpr> {
    constructor(context: Context, text: String) : this(context) {
        withoutUndo { setText(text) }
    }

    constructor(context: Context, editor: ScExprEditor<*>) : this(context) {
        withoutUndo { expand(editor) }
    }

    override fun defaultResult(): ScExpr = EmptyExpr

    override fun autoExpand(text: String): Boolean {
        val editor = when {
            text in Operator.map -> OperatorExprEditor(context, operator = OperatorEditor(context, text))
            text.endsWith(".") && text.dropLast(1).toIntOrNull() == null ->
                MessageSendEditor(context, ScExprExpander(context, text.dropLast(1)))

            text == "=" -> AssignmentEditor(context)
            text == "." -> MessageSendEditor(context)
            text == ":" -> NamedExprEditor(context)

            text.endsWith("=") && Identifier.isValid(text.dropLast(1)) ->
                AssignmentEditor(context, IdentifierEditor(context, text.dropLast(1)))

            text.endsWith(":") && Identifier.isValid(text.dropLast(1)) ->
                NamedExprEditor(context, IdentifierEditor(context, text.dropLast(1)))

            text.endsWith("(") && Identifier.isValid(text.dropLast(1)) ->
                NewObjectEditor(context, IdentifierEditor(context, text.dropLast(1)))

            text == "\\" -> SymbolLiteralEditor(context)
            text == "'" -> StringLiteralEditor(context)
            text == "{" -> ScFunctionEditor(context)
            text == "(" -> CodeBlockEditor(context)
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
        val value = withoutUndo { copy() }
        val variable = IdentifierEditor(context)
        val assignment = AssignmentEditor(context, variable, value)
        expand(assignment)
        variable.notifyViews { focus() }
    }

    @ProvideCommand(shortName = "name", type = Command.Type.SingleReceiver)
    fun nameValue() = makeUndoableEdit("Wrap in named value") {
        val value = withoutUndo { copy() }
        val variable = IdentifierEditor(context)
        val named = NamedExprEditor(context, variable, value)
        expand(named)
        variable.notifyViews { focus() }
    }

    @ProvideCommand(shortName = "send", type = Command.Type.SingleReceiver)
    fun callMethod() = makeUndoableEdit("Wrap in method call") {
        val receiver = withoutUndo { copy() }
        val send = MessageSendEditor(context, receiver)
        expand(send)
        send.method.notifyViews { focus() }
    }

    companion object {
        val config = ExpanderConfig<ScExprEditor<*>>(fallback = LiteralExpander.config).apply {
            "at" expand { ctx -> AccessKeyEditor(ctx) }
            "array" expand { ctx -> ArrayExprEditor(ctx) }
            "if" expand { ctx -> IfExprEditor(ctx) }
            "while" expand { ctx -> WhileExprEditor(ctx) }
            "assign" expand { ctx -> AssignmentEditor(ctx) }
            "eq" expand { ctx -> OperatorExprEditor(ctx, operator = OperatorEditor(ctx, "==")) }
            "concat" expand { ctx -> OperatorExprEditor(ctx, operator = OperatorEditor(ctx, "++")) }
            "tuple" expand { ctx -> TupleExprEditor(ctx) }
            "named" expand { ctx -> NamedExprEditor(ctx) }
            "block" expand { ctx -> CodeBlockEditor(ctx) }
            "function" expand { ctx -> ScFunctionEditor(ctx) }
            "new" expand { ctx -> NewObjectEditor(ctx) }
            "bus" expand { ctx -> BusSelector(ctx) }
            "buffer" expand { ctx -> BufferSelector(ctx) }
            "group" expand { ctx -> GroupSelector(ctx) }
        }
    }
}