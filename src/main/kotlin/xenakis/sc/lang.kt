package xenakis.sc

import hextant.codegen.*
import hextant.completion.CompletionStrategy
import hextant.completion.ConfiguredCompleter
import hextant.core.editor.TokenType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import xenakis.impl.ScWriter
import xenakis.model.GroupObjectReference
import xenakis.sc.editor.*
import java.io.StringWriter

@Serializable
sealed interface ScElement {
    val isValid: Boolean get() = true

    val children: List<ScElement> get() = emptyList()

    fun code(writer: ScWriter)
}

val ScElement.code: String
    get() {
        val writer = StringWriter()
        val scWriter = ScWriter(writer)
        code(scWriter)
        return writer.toString()
    }

@EditorInterface(ScExprEditor::class)
@UseEditor(ScExprExpander::class)
@ListEditor(serializable = true)
interface ScExpr : ScElement

@Serializable
abstract class SimpleScElement(val code: String) : ScElement {
    override fun code(writer: ScWriter) = writer.append(code)

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
@ListEditor(serializable = true)
sealed interface Literal : ScExpr {
    companion object : TokenType<Literal> {
        override fun compile(token: String): Literal {
            if (token.isEmpty()) return EmptyExpr
            token.toDoubleOrNull()?.let { value -> return DoubleLiteral(token, value) }
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

    override fun code(writer: ScWriter) {
        writer.append("/*unrecognized*/$text/*unrecognized*/")
    }
}

object EmptyExpr : Literal, Invalid {
    override fun code(writer: ScWriter) {
        writer.append("")
    }
}

@Serializable
data class BooleanLiteral(val value: Boolean) : Literal, SimpleScElement("$value")

@Serializable
object Nil : Literal, SimpleScElement("nil")

@Token(serializable = true)
@Serializable(with = DoubleLiteral.Serializer::class)
data class DoubleLiteral(val text: String, val valueOrNull: Double?) : Literal, SimpleScElement(text) {
    constructor(value: Double) : this(value.toString(), value)

    override val isValid: Boolean
        get() = valueOrNull != null

    fun get(): Double = valueOrNull ?: 0.0

    companion object : TokenType<DoubleLiteral> {
        override fun compile(token: String): DoubleLiteral = DoubleLiteral(token, token.toDoubleOrNull())
    }

    object Serializer : SimpleScElement.Serializer<DoubleLiteral>() {
        override fun fromString(str: String): DoubleLiteral = compile(str)
    }
}

@Serializable
@Token(nodeType = Literal::class)
data class SymbolLiteral(val name: String) : Literal, SimpleScElement("'$name'")

@Serializable
@Token(nodeType = Literal::class)
data class StringLiteral(val value: String) : Literal, SimpleScElement("\"$value\"")

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class ArrayExpr(val elements: List<ScExpr>) : ScExpr {
    override val isValid: Boolean
        get() = elements.all { it.isValid }

    override val children: List<ScElement>
        get() = elements

    override fun code(writer: ScWriter) = with(writer) {
        append("[")
        appendList(elements, separator = ", ")
        append("]")
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
@ListEditor(serializable = true)
data class NamedExpr(val name: Identifier, val value: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = name.isValid && value.isValid

    override val children: List<ScElement>
        get() = listOf(name, value)

    override fun code(writer: ScWriter) {
        name.code(writer)
        writer.append(": ")
        value.code(writer)
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class TupleExpr(val elements: List<NamedExpr>) : Literal {
    override val isValid: Boolean
        get() = elements.all { it.isValid }

    override val children: List<ScElement>
        get() = elements

    override fun code(writer: ScWriter) = with(writer) {
        append("(")
        appendList(elements, separator = ", ")
        append(")")
    }
}

@Serializable
@Compound(nodeType = Literal::class, serializable = true)
data class LiteralArray(val elements: List<Literal>) : Literal {
    override val isValid: Boolean
        get() = elements.all { it.isValid }

    override val children: List<ScElement>
        get() = elements

    override fun code(writer: ScWriter) = with(writer) {
        append("[")
        appendList(elements, separator = ", ")
        append("]")
    }
}

@Serializable
@Token(nodeType = ScExpr::class, serializable = true)
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
    override fun code(writer: ScWriter) = writer.append(code)
}

@Serializable
@Compound(serializable = true, nodeType = ScExpr::class)
data class CodeBlock(val variables: List<Identifier> = emptyList(), val statements: List<ScExpr>) : ScExpr {
    override val isValid: Boolean
        get() = variables.all { it.isValid } && statements.all { it.isValid }

    override val children: List<ScElement>
        get() = variables + statements

    override fun code(writer: ScWriter) {
        if (variables.isNotEmpty() || statements.isNotEmpty()) {
            writer.appendGroup { writeCode(this) }
        }
    }

    fun writeCode(writer: ScWriter) = with(writer) {
        if (variables.isNotEmpty()) {
            append("var ")
            appendList(variables, separator = ", ")
            appendLine(";")
        }
        for (expr in statements) {
            expr.code(this)
            appendLine(";")
        }
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class ScFunction(val parameters: List<Identifier> = emptyList(), val body: CodeBlock) : ScExpr {
    override val children: List<ScElement>
        get() = parameters + body

    override val isValid: Boolean
        get() = parameters.all { it.isValid } && body.isValid

    override fun code(writer: ScWriter) = writer.appendBlock("") {
        if (parameters.isNotEmpty()) {
            append("arg ")
            appendList(parameters, separator = ", ")
            appendLine(";")
        }
        body.writeCode(this)
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class Assignment(val variable: Identifier, val expression: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = variable.isValid && expression.isValid

    override val children: List<ScElement>
        get() = listOf(variable, expression)

    override fun code(writer: ScWriter) {
        writer.append("${variable.text} = ")
        expression.code(writer)
    }
}

@Serializable
sealed interface Selector

@Serializable(Operator.Ser::class)
@Token(serializable = true)
sealed class Operator(val code: String) : Selector, ScElement {
    override val isValid: Boolean
        get() = this !is Unrecognized

    override fun toString(): String = code

    override fun code(writer: ScWriter) = writer.append(code)

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
@Compound(nodeType = ScExpr::class, serializable = true)
data class MessageSend(val receiver: ScExpr, val method: Identifier, val arguments: List<ScExpr>) : ScExpr {
    override val isValid: Boolean
        get() = receiver.isValid && method.isValid && arguments.all { it.isValid }

    override val children: List<ScElement>
        get() = listOf(receiver, method) + arguments

    override fun code(writer: ScWriter) = with(writer) {
        receiver.code(writer)
        writer.append(".")
        method.code(writer)
        if (arguments.isNotEmpty()) {
            append("(")
            appendList(arguments, separator = ", ")
            append(")")
        }
    }
}

fun ScExpr.send(message: String, vararg arguments: ScExpr) =
    MessageSend(this, Identifier(message), arguments.asList())

fun lambda(expr: ScExpr) = ScFunction(emptyList(), CodeBlock(emptyList(), listOf(expr)))

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class OperatorExpr(val left: ScExpr, val operator: Operator, val right: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = left.isValid && operator.isValid && right.isValid

    override val children: List<ScElement>
        get() = listOf(left, operator, right)

    override fun code(writer: ScWriter) {
        writer.append("(")
        left.code(writer)
        writer.append(" ${operator.code} ")
        right.code(writer)
        writer.append(")")
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class NewObject(val className: Identifier, val arguments: List<ScExpr>) : ScExpr {
    override val isValid: Boolean
        get() = className.isValid && arguments.all { it.isValid }

    override val children: List<ScElement>
        get() = listOf(className) + arguments

    override fun code(writer: ScWriter) = with(writer) {
        className.code(writer)
        append("(")
        appendList(arguments, ", ")
        append(")")
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class AccessKey(val receiver: ScExpr, val key: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = receiver.isValid && key.isValid

    override val children: List<ScElement>
        get() = listOf(receiver, key)

    override fun code(writer: ScWriter) = with(writer) {
        receiver.code(writer)
        append("[")
        key.code(writer)
        append("]")
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class SpreadArray(val array: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = array.isValid

    override val children: List<ScElement>
        get() = listOf(array)

    override fun code(writer: ScWriter) {
        writer.append("*")
        array.code(writer)
    }
}

@Serializable
@Compound(serializable = true)
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

    override fun code(writer: ScWriter) {
        writer.append("VSTPlugin.ar(${input.code}, $channels, id: '$id')")
    }
}

@Compound(serializable = true, nodeType = ScExpr::class)
@Serializable
data class AdhocSynth(
    val name: Identifier,
    val block: CodeBlock,
    @Component(GroupSelector::class) val group: GroupObjectReference,
) : ScExpr {
    override val isValid: Boolean
        get() = block.isValid

    override val children: List<ScElement>
        get() = listOf(block)

    override fun code(writer: ScWriter) = writeCode(
        writer,
        synthName = "~adhoc_${name.text}",
        target = group.get().variableName,
        addAction = "addToHead",
        wrapInTask = false
    )

    fun writeCode(writer: ScWriter, synthName: String, target: String, addAction: String, wrapInTask: Boolean) =
        with(writer) {
            val plugins = block.statements.flatMap { s -> s.allChildren<VSTPlugin>() }
            if (wrapInTask && plugins.isNotEmpty()) {
                appendBlock("Task") {
                    writeCodeInsideTask(plugins, synthName, writer, target, addAction)
                }
                +".play"
            } else {
                writeCodeInsideTask(plugins, synthName, writer, target, addAction)
            }
        }

    private fun ScWriter.writeCodeInsideTask(
        plugins: List<VSTPlugin>, synthName: String, writer: ScWriter, target: String, addAction: String
    ) {
        appendBlock("$synthName = SynthDef(\\${name.text})") {
            block.writeCode(writer)
        }
        +".add.play($target, addAction: $addAction)"
        if (plugins.isEmpty()) return
        +"s.sync"
        +"1.wait"
        for (plugin in plugins) {
            val pluginName = plugin.pluginName
            val presetName = plugin.presetName
            val action = "action: { |c| if (c.info.presets.any { |p| p.name == \"$presetName\" }) { " +
                    "c.loadPreset('$presetName') " +
                    "}}"
            +"~ctrl_$presetName = VSTPluginController($synthName, id: '${plugin.id}').open('$pluginName.vst3', $action)"
        }
    }
}