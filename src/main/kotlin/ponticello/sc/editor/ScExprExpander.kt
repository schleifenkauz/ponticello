package ponticello.sc.editor

import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.command.Command
import hextant.command.meta.ProvideCommand
import hextant.core.editor.*
import hextant.serial.parentChain
import kotlinx.serialization.json.*
import ponticello.model.ctx.PonticelloContext
import ponticello.sc.*
import reaktive.value.*
import reaktive.value.binding.map

class ScExprExpander() : ConfiguredExpander<ScExpr, ScExprEditor<*>>(), ScExprEditor<ScExpr> {
    private val disabled: ReactiveVariable<Boolean> = reactiveVariable(false)

    val isDisabled: ReactiveBoolean get() = disabled

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

    override fun transform(result: ScExpr): ReactiveValue<ScExpr> =
        isDisabled.map { value -> if (value) DisabledExpr(result) else result }

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
                AssignableExprExpander(text.dropLast(1)),
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
            editor is AssignmentEditor && !editor.assignee.text.now.isNullOrEmpty() ->
                editor.expression.notifyViews { receiveFocus() }

            editor is MessageSendEditor && editor.receiver.result.now != EmptyExpr ->
                editor.method.notifyViews { receiveFocus() }

            editor is NamedExprEditor && editor.name.text.now != "" ->
                editor.value.notifyViews { receiveFocus() }
        }
    }

    fun toggleDisabled() {
        if (isDisabled.now) {
            enable()
        } else {
            val disabledParent = parentChain()
                .filterIsInstance<ScExprExpander>()
                .firstOrNull { ed -> ed.isDisabled.now }
            if (disabledParent != null) {
                disabledParent.enable()
            } else {
                disable()
            }
        }
    }

    private fun enable() {
        VariableEdit.updateVariable(disabled, false, context[UndoManager], "Uncomment expression")
    }

    private fun disable() {
        VariableEdit.updateVariable(disabled, true, context[UndoManager], "Comment expression")
    }

    override fun onReset(editor: ScExprEditor<*>) {
        disabled.set(false)
    }

    override fun compile(token: String): ScExpr {
        Literal.compile(token).takeIf { it !is Invalid }?.let { return it }
        if (Identifier.isValid(token)) return Identifier(token)
        return when {
            token == "" -> EmptyExpr
            else -> UnrecognizedToken(token)
        }
    }

    override fun serialize(): JsonElement {
        return when (val element = super.serialize()) {
            is JsonObject -> JsonObject(element + ("disabled" to JsonPrimitive(isDisabled.now)))

            else -> element
        }
    }

    override fun deserialize(element: JsonElement) {
        super.deserialize(element)
        if (element is JsonObject) {
            disabled.set(element["disabled"]?.jsonPrimitive?.boolean ?: false)
        }
    }

    @ProvideCommand(
        shortName = "assign", name = "Wrap in assignment",
        type = Command.Type.SingleReceiver
    )
    fun assignToVariable() {
        val value = snapshot()
        val variable = AssignableExprExpander("")
        val assignment = AssignmentEditor(variable, value)
        expand(assignment, editDescription = "Wrap in assignment")
        variable.notifyViews { focus() }
    }

    @ProvideCommand(
        shortName = "name", name = "Wrap in named value",
        type = Command.Type.SingleReceiver
    )
    fun nameValue() {
        val value = snapshot()
        val variable = IdentifierEditor()
        val named = NamedExprEditor(variable, value)
        expand(named, editDescription = "Wrap in named value")
        variable.notifyViews { focus() }
    }

    @ProvideCommand(
        shortName = "send", name = "Wrap in method call",
        type = Command.Type.SingleReceiver
    )
    fun callMethod() {
        val receiver = snapshot()
        val send = MessageSendEditor(receiver, IdentifierEditor(""), ScExprListEditor().defaultState())
        expand(send, editDescription = "Wrap in method call")
        send.method.notifyViews { focus() }
    }

    companion object {
        val config = ExpanderConfig<ScExprEditor<*>>(fallback = LiteralExpander.config).apply {
            "at".expand { AccessKeyEditor().defaultState() }
            "array".expand { ArrayExprEditor().defaultState() }
            "if".expand { IfExprEditor().defaultState() }
            "while".expand(Expander<*, *>::isStatementInBlock) { _ -> WhileExprEditor().defaultState() }
            "loop".expand(Expander<*, *>::isStatementInBlock) { _ -> LoopExprEditor().defaultState() }
            "play".expand { PlayObjectEditor().defaultState() }
            "synth".expand { SynthExprEditor().defaultState() }
            "assign".expand { AssignmentEditor().defaultState() }
            "eq".expand {
                OperatorExprEditor(
                    ScExprExpander().defaultState(),
                    operator = OperatorEditor("=="),
                    ScExprExpander().defaultState()
                )
            }
            "concat".expand {
                OperatorExprEditor(
                    operator = OperatorEditor("++"),
                    left = ScExprExpander().defaultState(),
                    right = ScExprExpander().defaultState()
                )
            }
            "exp".expand {
                OperatorExprEditor(
                    operator = OperatorEditor("**"),
                    left = ScExprExpander().defaultState(),
                    right = ScExprExpander().defaultState()
                )
            }
            "tuple".expand { _ -> TupleExprEditor().defaultState() }
            "named".expand { NamedExprEditor().defaultState() }
            "block".expand(Expander<*, *>::isStatementInBlock) { CodeBlockEditor().defaultState() }
            "lambda".expand { _ -> ScFunctionEditor().defaultState() }
            "bus".expand { BusSelector().defaultState() }
            "buf".expand { BufferSelector().defaultState() }
            "obj".expand { ScoreObjectSelector().defaultState() }
            "pattern".expand { GlobalPatternSelector().defaultState() }
            "control".expand(
                condition = { exp ->
                    exp.context.hasProperty(PonticelloContext) &&
                            exp.context[PonticelloContext].associatedObject != null
                },
                create = { ParameterReferenceEditor().defaultState() }
            )
            "adhoc-synth".expand { AdhocSynthEditor().defaultState() }
            "def".expand(Expander<*, *>::isStatementInBlock) { _ -> FunctionDefEditor().defaultState() }
            "in".expand { InExprEditor().defaultState() }
            "out".expand { OutExprEditor().defaultState() }
        }
    }
}