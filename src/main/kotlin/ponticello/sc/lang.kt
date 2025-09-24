package ponticello.sc

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
import ponticello.impl.Decimal
import ponticello.impl.parseDecimal
import ponticello.impl.zero
import ponticello.sc.client.ScWriter
import ponticello.sc.editor.*
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
interface ScExpr : ScElement {
    fun getLfo(): LFO? = null
}

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

@UseEditor(AssignableExprExpander::class)
interface AssignableScExpr : ScExpr

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

data class UnrecognizedToken(val text: String) : Literal, Invalid, AssignableScExpr {
    override val isValid: Boolean
        get() = false

    override fun code(writer: ScWriter, context: Context) {
        writer.append("/*unrecognized*/$text/*unrecognized*/")
    }
}

object EmptyExpr : Literal, Invalid, AssignableScExpr {
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
data class DecimalLiteral(val text: String, val valueOrNull: Decimal?) : Literal, SimpleScElement(text), ScExpr {
    override fun getLfo(): LFO = ConstantLFO(get().value)

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
data class Identifier(val text: String) : SimpleScElement(text), AssignableScExpr, ScExpr {
    override val isValid: Boolean
        get() = isValid(text)

    val isValidClassName: Boolean
        get() = isValid && text[0].isUpperCase()

    override fun getLfo(): LFO? {
        if (!text.startsWith("~ctrl_")) return null
        return ParameterNameLFO(text.removePrefix("~ctrl_"))
    }

    companion object {
        fun isValid(token: String): Boolean {
            if (token.isEmpty()) return false
            if (!token.first().isLetter() && token.first() != '~') return false
            return token.drop(1).all { c -> c.isLetterOrDigit() || c == '_' }
        }

        fun truncate(token: String) = token.filter { it.isLetterOrDigit() || it == '_' }
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

    override fun code(writer: ScWriter, context: Context) = with(writer) {
        if (body.statements.size <= 1 && body.variables.isEmpty()) {
            append("{ ")
            if (parameters.isNotEmpty()) append(parameters.joinToString(", ", "|", "| ") { id -> id.text })
            body.statements.singleOrNull()?.code(writer, context)
            append("}")
        } else {
            appendBlock("", endLine = false) {
                if (parameters.isNotEmpty()) {
                    append("arg ")
                    appendList(parameters, separator = ", ", context)
                    appendLine(";")
                }
                body.writeCode(writer, context)
            }
        }
    }
}

@Serializable
@Compound(nodeType = ScExpr::class)
data class Assignment(
    val assignee: AssignableScExpr,
    val expression: ScExpr,
) : ScExpr {
    override val isValid: Boolean
        get() = assignee.isValid && expression.isValid

    override val children: List<ScElement>
        get() = listOf(assignee, expression)

    override fun code(writer: ScWriter, context: Context) {
        assignee.code(writer, context)
        writer.append(" = ")
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
        if (method != Identifier("new") || arguments.isEmpty()) {
            writer.append(".")
            method.code(writer, context)
        }
        if (arguments.isNotEmpty()) {
            append("(")
            appendList(arguments, separator = ", ", context)
            append(")")
        }
    }

    override fun getLfo(): LFO? {
        val receiverLFO = receiver.getLfo()
        return when (method.text) {
            "kr", "ar" -> {
                if (receiver !is Identifier) return null
                val freq = arguments.getOrNull(0)?.getLfo() ?: return null
                val phase =
                    if (arguments.size == 2) (arguments[1] as? DecimalLiteral)?.get()?.value ?: return null
                    else 0.0
                when (receiver.text) {
                    "SinOsc" -> Sine(freq, phase)
                    "Saw", "LFSaw" -> Sawtooth(freq, phase)
                    else -> null
                }
            }

            "range", "exprange" -> {
                if (receiverLFO == null) return null
                if (arguments.size != 2) return null
                val min = arguments[0].getLfo() ?: return null
                val max = arguments[1].getLfo() ?: return null
                when (method.text) {
                    "range" -> LinRange(receiverLFO, min, max)
                    "exprange" -> ExpRange(receiverLFO, min, max)
                    else -> null
                }
            }

            "unipolar", "bipolar" ->  {
                if (receiverLFO == null) return null
                if (arguments.size != 1) return null
                val mult = arguments[0].getLfo() ?: return null
                when (method.text) {
                    "unipolar" -> LinRange(receiverLFO, ConstantLFO(0.0), mult)
                    "bipolar" -> LinRange(receiverLFO, mult, MulLFO(mult, ConstantLFO(-1.0)))
                    else -> null
                }
            }

            else -> null
        }
    }
}

@Serializable
@Compound(nodeType = ScExpr::class)
data class PropertyAccessExpr(val receiver: ScExpr, val property: Identifier) : ScExpr, AssignableScExpr {
    override val isValid: Boolean
        get() = receiver.isValid && property.isValid


    override val children: List<ScElement>
        get() = listOf(receiver, property)

    override fun code(writer: ScWriter, context: Context) {
        receiver.code(writer, context)
        writer.append(".")
        property.code(writer, context)
    }
}

@Serializable
@Compound(nodeType = ScExpr::class)
data class TopLevelFunctionCall(val function: Identifier, val arguments: List<ScExpr>) : ScExpr {
    override val isValid: Boolean
        get() = function.isValid && arguments.all { it.isValid }

    override val children: List<ScElement>
        get() = listOf(function) + arguments

    override fun code(writer: ScWriter, context: Context) = with(writer) {
        function.code(writer, context)
        append("(")
        appendList(arguments, separator = ", ", context)
        append(")")
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

    override fun getLfo(): LFO? {
        val left = left.getLfo() ?: return null
        val right = right.getLfo() ?: return null
        return when (operator) {
            Operator.Div -> DivLFO(left, right)
            Operator.Minus -> SubLFO(left, right)
            Operator.Plus -> AddLFO(left, right)
            Operator.Times -> MulLFO(left, right)
            else -> null
        }
    }

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
data class AccessKey(val receiver: ScExpr, val key: ScExpr) : ScExpr, AssignableScExpr {
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
data class DisabledExpr(val expr: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = true

    override fun code(writer: ScWriter, context: Context) {
        writer.append("/*")
        expr.code(writer, context)
        writer.append("*/")
    }
}