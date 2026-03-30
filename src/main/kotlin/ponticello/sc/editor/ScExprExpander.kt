package ponticello.sc.editor

import bundles.getOrNull
import fxutils.runAfterLayout
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.command.Command
import hextant.command.meta.ProvideCommand
import hextant.core.editor.*
import hextant.serial.parentChain
import kotlinx.serialization.json.*
import ponticello.model.code.GlobalPatternObject
import ponticello.model.ctx.PonticelloContext
import ponticello.model.instr.BusObject
import ponticello.model.score.ScoreObject
import ponticello.model.server.BufferObject
import ponticello.sc.*
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

class ScExprExpander() : AbstractScExprExpander<ScExpr>() {
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

    override fun transform(result: ScExpr): ScExpr = if (isDisabled.now) DisabledExpr(result) else result

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
            text.endsWith("(") && Identifier.isValid(text.dropLast(1)) -> {
                val function = IdentifierEditor(text.dropLast(1))
                val arguments = ScExprListEditor(ScExprExpander().defaultState())
                TopLevelFunctionCallEditor(function, arguments)
            }

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

            editor is TopLevelFunctionCallEditor && editor.function.text.now != "" -> {
                editor.arguments.notifyViews { receiveFocus() }
            }

            else -> super.onExpansion(editor)
        }
    }

    @ProvideCommand(
        "Toggle comment", shortName = "toggle-comment",
        defaultShortcut = "Alt+D", type = Command.Type.MultipleReceivers
    )
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
        toggledDisabled()
    }

    private fun disable() {
        VariableEdit.updateVariable(disabled, true, context[UndoManager], "Comment expression")
        toggledDisabled()
    }

    override fun onReset(editor: ScExprEditor<*>) {
        disabled.set(false)
        toggledDisabled()
    }

    private fun toggledDisabled() {
        if (!isInitialized) return
        val res = result.now
        when {
            isDisabled.now && res !is DisabledExpr -> _result.set(DisabledExpr(res))
            !isDisabled.now && res is DisabledExpr -> _result.set(res.expr)
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
            toggledDisabled()
        }
    }

    @ProvideCommand(
        shortName = "assign", name = "Wrap in assignment",
        type = Command.Type.SingleReceiver, defaultShortcut = "Alt+V"
    )
    fun wrapInVariableAssignment() {
        val value = snapshot()
        val variable = AssignableExprExpander("")
        val assignment = AssignmentEditor(variable, value)
        expand(assignment, editDescription = "Wrap in assignment")
        variable.notifyViews { select() }
    }

    @ProvideCommand(
        shortName = "name", name = "Wrap in named value",
        type = Command.Type.SingleReceiver, defaultShortcut = "Alt+N"
    )
    fun wrapInNamedValue() {
        val value = snapshot()
        val variable = IdentifierEditor().defaultState()
        val named = NamedExprEditor(variable, value)
        expand(named, editDescription = "Wrap in named value")
        variable.notifyViews { select() }
    }

    @ProvideCommand(
        shortName = "send", name = "Wrap in method call",
        type = Command.Type.SingleReceiver, defaultShortcut = "Alt+PERIOD"
    )
    fun wrapInMethodCall() {
        val receiver = snapshot()
        val send = MessageSendEditor(
            receiver = receiver,
            method = IdentifierEditor().defaultState(),
            arguments = ScExprListEditor().defaultState()
        )
        expand(send, editDescription = "Wrap in method call")
        send.method.notifyViews { select() }
    }

    @ProvideCommand(
        "Wrap in function", shortName = "wrap-func",
        type = Command.Type.SingleReceiver, defaultShortcut = "BRACELEFT"
    )
    fun wrapInFunction() {
        val newEditor = ScFunctionEditor(
            body = CodeBlockEditor(
                statements = ScExprListEditor(this.snapshot())
            )
        )
        expand(newEditor, editDescription = "Wrap in function")
        runAfterLayout {
            newEditor.parameters.notifyViews { select() }
        }
    }

    @ProvideCommand(
        "Wrap in array", shortName = "wrap-array",
        type = Command.Type.SingleReceiver, defaultShortcut = "OPEN_BRACKET"
    )
    fun wrapInArray() {
        val newEditor = ArrayExprEditor(elements = ScExprListEditor(this.snapshot()))
        expand(newEditor, editDescription = "Wrap in array")
        runAfterLayout {
            newEditor.notifyViews { select() }
        }
    }

    @ProvideCommand(
        "Wrap in operator expression", shortName = "wrap-binop",
        type = Command.Type.SingleReceiver, defaultShortcut = "Alt+B"
    )
    fun wrapInBinaryOperatorExpr() {
        val newEditor = OperatorExprEditor(
            left = this.snapshot(),
            operator = OperatorEditor().defaultState(),
            right = ScExprExpander().defaultState()
        )
        expand(newEditor, editDescription = "Wrap in operator expression")
        runAfterLayout {
            newEditor.operator.notifyViews { select() }
        }
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
            "call".expand {
                TopLevelFunctionCallEditor(
                    function = IdentifierEditor().defaultState(),
                    arguments = ScExprListEditor(ScExprExpander().defaultState())
                )
            }
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
            "meter".expand { MeterSelector().defaultState() }
            "buf".expand { BufferSelector().defaultState() }
            "obj".expand { ScoreObjectSelector().defaultState() }
            "pattern".expand { GlobalPatternSelector().defaultState() }
            "control".expand(
                condition = { exp -> exp.context.getOrNull(PonticelloContext) is PonticelloContext.Control },
                create = { ParameterReferenceEditor().defaultState() }
            )
            "adhoc-synth".expand { AdhocSynthEditor().defaultState() }
            "def".expand(Expander<*, *>::isStatementInBlock) { _ -> FunctionDefEditor().defaultState() }
            "in".expand { InExprEditor().defaultState() }
            "out".expand { OutExprEditor().defaultState() }
            expandInContext("transform", PonticelloContext.SynthDef::class) { _, _ ->
                TransformSignalEditor(
                    bus = ScExprExpander().defaultState(),
                    mix = ScExprExpander("1"),
                    signalVar = IdentifierEditor("sig"),
                    body = CodeBlockEditor(
                        variables = IdentifierListEditor().defaultState(),
                        statements = ScExprListEditor(ScExprExpander("sig"))
                    )
                )
            }
            "slider".expand { expander ->
                val context = expander.context[PonticelloContext]
                val spec = if (context is PonticelloContext.Control) {
                    context.control.spec.now as? NumericalControlSpec
                        ?: NumericalControlSpec.DEFAULT
                } else NumericalControlSpec.DEFAULT
                SliderExprEditor(spec)
            }
            "goto".expand(condition = { exp ->
                val ctx = exp.context[PonticelloContext]
                ctx is PonticelloContext.RoutineDef || ctx is PonticelloContext.Task
            }) { _ -> GoToEditor().defaultState() }

            "var".expand(condition = { exp -> exp.getParent<CodeBlockEditor>() != null }) { exp ->
                val enclosingBlock = exp.getParent<CodeBlockEditor>() ?: return@expand null
                val variable = IdentifierEditor().defaultState()
                enclosingBlock.variables.addLast(variable)
                val assignee = AssignableExprExpander()
                assignee.bindToDefinition(variable)
                AssignmentEditor(assignee).defaultState()
            }

            "param".expand(condition = { exp -> exp.getParent<ScFunctionEditor>() != null }) { exp ->
                val enclosingFunction = exp.getParent<ScFunctionEditor>() ?: return@expand null
                val parameter = IdentifierEditor().defaultState()
                enclosingFunction.parameters.addLast(parameter)
                if (exp is AbstractScExprExpander) {
                    exp.bindToDefinition(parameter)
                }
                exp.setText("")
                null
            }

            registerInterceptor { item: BusObject, _ -> BusSelector().selectInitial(item) }
            registerInterceptor { item: ScoreObject, _ -> ScoreObjectSelector().selectInitial(item) }
            registerInterceptor { item: BufferObject, _ -> BufferSelector().selectInitial(item) }
            registerInterceptor { item: GlobalPatternObject, _ -> GlobalPatternSelector().selectInitial(item) }
        }
    }
}