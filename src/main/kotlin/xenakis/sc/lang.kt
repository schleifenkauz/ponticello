package xenakis.sc

import hextant.codegen.*
import hextant.completion.CompletionStrategy
import hextant.completion.ConfiguredCompleter
import hextant.context.Context
import hextant.core.editor.TokenType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import xenakis.impl.Decimal
import xenakis.impl.parseDecimal
import xenakis.impl.superColliderPath
import xenakis.impl.zero
import xenakis.model.obj.GroupReference
import xenakis.model.project.XenakisProject.Companion.projectDirectory
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.*
import java.io.StringWriter

@Serializable
sealed interface ScElement {
    val isValid: Boolean get() = true

    val children: List<ScElement> get() = emptyList()

    fun code(writer: ScWriter, context: Context)
}

fun ScElement.code(context: Context): String {
    val writer = StringWriter()
    val scWriter = ScWriter(writer)
    code(scWriter, context)
    return writer.toString()
}

@EditorInterface(ScExprEditor::class)
@UseEditor(ScExprExpander::class)
@ListEditor
interface ScExpr : ScElement

@Serializable
abstract class SimpleScElement(val code: String) : ScElement {
    override fun code(writer: ScWriter, context: Context) = writer.append(code)

    abstract class Serializer<T : SimpleScElement> : KSerializer<T> {
        override val descriptor: SerialDescriptor
            get() = serialDescriptor<String>()

        abstract fun fromString(str: String): T

        override fun deserialize(decoder: Decoder): T = fromString(decoder.decodeString())

        override fun serialize(encoder: Encoder, value: T) {
            encoder.encodeString(value.code)
        }
    }
}

@Serializable
@EditorInterface(LiteralEditor::class)
@UseEditor(LiteralExpander::class)
@ListEditor
sealed interface Literal : ScExpr {
    companion object : TokenType<Literal> {
        override fun compile(token: String): Literal {
            if (token.isEmpty()) return EmptyExpr
            token.toDoubleOrNull()?.let { _ -> return DecimalLiteral.compile(token) }
            if (token == "true") return BooleanLiteral(true)
            if (token == "false") return BooleanLiteral(false)
            if (token == "nil") return Nil
            return UnrecognizedToken(token)
        }
    }
}

sealed interface Invalid

data class UnrecognizedToken(val text: String) : Literal, Invalid {
    override val isValid: Boolean
        get() = false

    override fun code(writer: ScWriter, context: Context) {
        writer.append("/*unrecognized*/$text/*unrecognized*/")
    }
}

object EmptyExpr : Literal, Invalid {
    override fun code(writer: ScWriter, context: Context) {
        writer.append("")
    }
}

@Serializable
data class BooleanLiteral(val value: Boolean) : Literal, SimpleScElement("$value")

@Serializable
object Nil : Literal, SimpleScElement("nil")

@Token
@Serializable(with = DecimalLiteral.Serializer::class)
@SerialName("DoubleLiteral") //backward compatibility
data class DecimalLiteral(val text: String, val valueOrNull: Decimal?) : Literal, SimpleScElement(text) {
    constructor(value: Decimal) : this(value.toString(), value)

    override fun toString(): String = text

    override val isValid: Boolean
        get() = valueOrNull != null

    fun get(): Decimal = valueOrNull ?: zero

    companion object : TokenType<DecimalLiteral> {

        override fun compile(token: String): DecimalLiteral = DecimalLiteral(token, token.parseDecimal())
    }

    object Serializer : SimpleScElement.Serializer<DecimalLiteral>() {
        override fun fromString(str: String): DecimalLiteral = compile(str)
    }
}

@Serializable
@Token(nodeType = Literal::class)
data class SymbolLiteral(val name: String) : Literal, SimpleScElement("'$name'")

@Serializable
@Token(nodeType = Literal::class)
data class StringLiteral(val value: String) : Literal, SimpleScElement("\"$value\"")

@Serializable
@Compound(nodeType = ScExpr::class)
data class ArrayExpr(val elements: List<ScExpr>) : ScExpr {
    override val isValid: Boolean
        get() = elements.all { it.isValid }

    override val children: List<ScElement>
        get() = elements

    override fun code(writer: ScWriter, context: Context) = with(writer) {
        append("[")
        appendList(elements, separator = ", ", context)
        append("]")
    }
}

@Serializable
@Compound(nodeType = ScExpr::class)
@ListEditor
data class NamedExpr(val name: Identifier, val value: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = name.isValid && value.isValid

    override val children: List<ScElement>
        get() = listOf(name, value)

    override fun code(writer: ScWriter, context: Context) {
        name.code(writer, context)
        writer.append(": ")
        value.code(writer, context)
    }
}

@Serializable
@Compound(nodeType = ScExpr::class)
data class TupleExpr(val elements: List<NamedExpr>) : Literal {
    override val isValid: Boolean
        get() = elements.all { it.isValid }

    override val children: List<ScElement>
        get() = elements

    override fun code(writer: ScWriter, context: Context) = with(writer) {
        append("(")
        appendList(elements, separator = ", ", context)
        append(")")
    }
}

@Serializable
@Compound(nodeType = Literal::class)
data class LiteralArray(val elements: List<Literal>) : Literal {
    override val isValid: Boolean
        get() = elements.all { it.isValid }

    override val children: List<ScElement>
        get() = elements

    override fun code(writer: ScWriter, context: Context) = with(writer) {
        append("[")
        appendList(elements, separator = ", ", context)
        append("]")
    }
}

@Serializable
@Token(nodeType = ScExpr::class)
@ListEditor
data class Identifier(val text: String) : SimpleScElement(text), ScExpr {
    override val isValid: Boolean
        get() = isValid(text)

    val isValidClassName: Boolean
        get() = isValid && text[0].isUpperCase()

    companion object {
        fun isValid(token: String): Boolean {
            if (token.isEmpty()) return false
            if (!token.first().isLetterOrDigit() && token.first() != '~') return false
            return token.drop(1).all { c -> c.isLetterOrDigit() || c == '_' }
        }

        fun truncate(token: String) = token.filter { it.isLetterOrDigit() }
    }
}

@Serializable
data class RawScExpr(val code: String) : ScExpr {
    override fun code(writer: ScWriter, context: Context) = writer.append(code)
}

@Serializable
@Compound(nodeType = ScExpr::class)
data class CodeBlock(val variables: List<Identifier> = emptyList(), val statements: List<ScExpr>) : ScExpr {
    override val isValid: Boolean
        get() = variables.all { it.isValid } && statements.all { it.isValid }

    override val children: List<ScElement>
        get() = variables + statements

    override fun code(writer: ScWriter, context: Context) {
        writer.appendGroup { writeCode(writer, context) }
    }

    fun writeCode(writer: ScWriter, context: Context) = with(writer) {
        if (variables.isNotEmpty()) {
            append("var ")
            appendList(variables, separator = ", ", context)
            appendLine(";")
        }
        for (expr in statements) {
            expr.code(this, context)
            appendLine(";")
        }
    }
}

@Serializable
@Compound(nodeType = ScExpr::class)
data class ScFunction(val parameters: List<Identifier> = emptyList(), val body: CodeBlock) : ScExpr {
    override val children: List<ScElement>
        get() = parameters + body

    override val isValid: Boolean
        get() = parameters.all { it.isValid } && body.isValid

    override fun code(writer: ScWriter, context: Context) = writer.appendBlock("", endLine = false) {
        if (parameters.isNotEmpty()) {
            append("arg ")
            appendList(parameters, separator = ", ", context)
            appendLine(";")
        }
        body.writeCode(writer, context)
    }
}

@Serializable
@Compound(nodeType = ScExpr::class)
data class Assignment(val variable: Identifier, val expression: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = variable.isValid && expression.isValid

    override val children: List<ScElement>
        get() = listOf(variable, expression)

    override fun code(writer: ScWriter, context: Context) {
        writer.append("${variable.text} = ")
        expression.code(writer, context)
    }
}

@Serializable
sealed interface Selector

@Serializable(Operator.Ser::class)
@Token
sealed class Operator(val code: String) : Selector, ScElement {
    override val isValid: Boolean
        get() = this !is Unrecognized

    override fun toString(): String = code

    override fun code(writer: ScWriter, context: Context) = writer.append(code)

    companion object : TokenType<Operator>, ConfiguredCompleter<Any?, Operator>(CompletionStrategy.simple) {
        val map = values().associateBy { op -> op.code }

        override fun compile(token: String): Operator = map[token] ?: Unrecognized(token)

        override fun completionPool(context: Any?): Collection<Operator> = values().asList()

        fun values(): Array<Operator> =
            arrayOf(Plus, Minus, Times, Div, Mod, Exp, Le, Leq, Gr, Greq, Eq, Neq, PlusPlus, Expansion)
    }

    object Plus : Operator("+")
    object Minus : Operator("-")
    object Times : Operator("*")
    object Div : Operator("/")
    object Mod : Operator("%")
    object Exp : Operator("**")
    object Le : Operator("<")
    object Leq : Operator("<=")
    object Gr : Operator(">")
    object Greq : Operator(">=")
    object Eq : Operator("==")
    object Neq : Operator("!=")
    object PlusPlus : Operator("++")
    object Expansion : Operator("!")
    object At : Operator("@")
    class Unrecognized(val text: String) : Operator(text)

    object Ser : KSerializer<Operator> {
        override val descriptor: SerialDescriptor = serialDescriptor<String>()

        override fun deserialize(decoder: Decoder): Operator = compile(decoder.decodeString())

        override fun serialize(encoder: Encoder, value: Operator) {
            encoder.encodeString(value.code)
        }
    }
}

@Serializable
@Compound(nodeType = ScExpr::class)
data class MessageSend(val receiver: ScExpr, val method: Identifier, val arguments: List<ScExpr>) : ScExpr {
    override val isValid: Boolean
        get() = receiver.isValid && method.isValid && arguments.all { it.isValid }

    override val children: List<ScElement>
        get() = listOf(receiver, method) + arguments

    override fun code(writer: ScWriter, context: Context) = with(writer) {
        receiver.code(writer, context)
        writer.append(".")
        method.code(writer, context)
        if (arguments.isNotEmpty()) {
            append("(")
            appendList(arguments, separator = ", ", context)
            append(")")
        }
    }
}

fun ScExpr.send(message: String, vararg arguments: ScExpr) =
    MessageSend(this, Identifier(message), arguments.asList())

fun lambda(parameters: List<Identifier>, expr: ScExpr) = ScFunction(parameters, CodeBlock(emptyList(), listOf(expr)))

fun lambda(vararg parameters: String, expr: () -> ScExpr) = lambda(parameters.map(::Identifier), expr())

fun lambda(expr: ScExpr) = lambda(emptyList(), expr)

@Serializable
@Compound(nodeType = ScExpr::class)
data class OperatorExpr(val left: ScExpr, val operator: Operator, val right: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = left.isValid && operator.isValid && right.isValid

    override val children: List<ScElement>
        get() = listOf(left, operator, right)

    override fun code(writer: ScWriter, context: Context) {
        writer.append("(")
        left.code(writer, context)
        writer.append(" ${operator.code} ")
        right.code(writer, context)
        writer.append(")")
    }
}

@Serializable
@Compound(nodeType = ScExpr::class)
data class AccessKey(val receiver: ScExpr, val key: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = receiver.isValid && key.isValid

    override val children: List<ScElement>
        get() = listOf(receiver, key)

    override fun code(writer: ScWriter, context: Context) = with(writer) {
        receiver.code(writer, context)
        append("[")
        key.code(writer, context)
        append("]")
    }
}

@Serializable
@Compound(nodeType = ScExpr::class)
data class SpreadArray(val array: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = array.isValid

    override val children: List<ScElement>
        get() = listOf(array)

    override fun code(writer: ScWriter, context: Context) {
        writer.append("*")
        array.code(writer, context)
    }
}

@Serializable
@Compound
data class EventDictionary(val entries: List<NamedExpr>)

@Serializable
data class VSTPlugin(
    val input: ScExpr,
    val channels: Int,
    val pluginName: String,
    val id: String,
    val presetName: String
) : ScExpr {
    override val isValid: Boolean
        get() = input.isValid

    override val children: List<ScElement>
        get() = listOf(input)

    override fun code(writer: ScWriter, context: Context) {
        writer.append("VSTPlugin.ar(${input.code(context)}, $channels, id: '$id')")
    }
}

@Compound(nodeType = ScExpr::class)
@Serializable
data class AdhocSynth(
    val name: Identifier,
    val block: CodeBlock,
    @Component(GroupSelector::class) val group: GroupReference,
) : ScExpr {
    override val isValid: Boolean
        get() = block.isValid

    override val children: List<ScElement>
        get() = listOf(block)

    override fun code(writer: ScWriter, context: Context) = writeCode(
        writer, context,
        synthName = "~adhoc_${name.text}",
        target = group.get()?.superColliderName ?: "<none>", //TODO
        addAction = "addToHead",
        wrapInTask = false
    )

    fun writeCode(
        writer: ScWriter,
        context: Context,
        synthName: String,
        target: String,
        addAction: String,
        wrapInTask: Boolean
    ) = with(writer) {
            val plugins = block.statements.flatMap { s -> s.allChildren<VSTPlugin>() }
            if (wrapInTask && plugins.isNotEmpty()) {
                appendBlock("Task", endLine = false) {
                    writeCodeInsideTask(context, plugins, synthName, target, addAction)
                }
                +".play"
            } else {
                writeCodeInsideTask(context, plugins, synthName, target, addAction)
            }
        }

    private fun ScWriter.writeCodeInsideTask(
        context: Context, plugins: List<VSTPlugin>,
        synthName: String, target: String, addAction: String
    ) {
        appendBlock("$synthName = SynthDef(\\${name.text})", endLine = false) {
            block.writeCode(writer, context)
        }
        +".add.play($target, addAction: $addAction)"
        if (plugins.isEmpty()) return
        +"s.sync"
        +"1.wait"
        for (plugin in plugins) {
            val pluginName = plugin.pluginName
            val presetName = plugin.presetName
            val presetFile = context[projectDirectory].resolve("presets").resolve("$presetName.fxp").superColliderPath
            val action = "action: { |c| if (PathName(${presetFile}).isFile) { c.readProgram(${presetFile}) } }"
            +"~ctrl_$presetName = VSTPluginController($synthName, id: '${plugin.id}').open('$pluginName.vst3', $action)"
        }
    }
}