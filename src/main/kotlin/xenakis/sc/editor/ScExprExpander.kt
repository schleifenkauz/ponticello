package xenakis.sc.editor

import hextant.command.Command
import hextant.command.meta.ProvideCommand
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.editor.ConfiguredExpander
import hextant.core.editor.ExpanderConfig
import hextant.core.editor.copy
import hextant.undo.makeUndoableEdit
import xenakis.sc.*

class ScExprExpander(context: Context) : ConfiguredExpander<ScExpr, ScExprEditor<*>>(config, context), ScExprEditor<ScExpr> {
    constructor(context: Context, text: String): this(context) {
        withoutUndo { setText(text) }
    }

    constructor(context: Context, editor: ScExprEditor<*>): this(context) {
        withoutUndo { expand(editor) }
    }

    override fun defaultResult(): ScExpr = EmptyExpr

    override fun autoExpand(text: String): ScExprEditor<*>? = when {
        text in Operator.map -> OperatorExprEditor(context, operator = OperatorEditor(context, text))
        text.endsWith(".") -> MessageSendEditor(context, ScExprExpander(context, text.dropLast(1)))
        text.endsWith("=") -> AssignmentEditor(context, IdentifierEditor(context, text.dropLast(1)))
        text == "\\" -> SymbolLiteralEditor(context)
        text == "'" -> StringLiteralEditor(context)
        text == "{" -> ScFunctionEditor(context)
        text == "(" -> CodeGroupEditor(context)
        else -> null
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
        val value = copy()
        val variable = IdentifierEditor(context)
        val assignment = AssignmentEditor(context, variable, value)
        expand(assignment)
    }

    @ProvideCommand(shortName = "send", type = Command.Type.SingleReceiver)
    fun callMethod() = makeUndoableEdit("Wrap in method call") {
        val receiver = copy()
        expand(MessageSendEditor(context, receiver))
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
        }
    }
}