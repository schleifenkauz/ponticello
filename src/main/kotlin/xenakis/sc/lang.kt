package xenakis.sc

import hextant.codegen.*
import hextant.completion.CompletionStrategy
import hextant.completion.ConfiguredCompleter
import hextant.core.editor.TokenType
import kotlinx.serialization.Serializable
import xenakis.sc.editor.LiteralEditor
import xenakis.sc.editor.LiteralExpander
import xenakis.sc.editor.ScExprEditor
import xenakis.sc.editor.ScExprExpander
import xenakis.impl.ScWriter
import xenakis.ui.format
import java.io.StringWriter

@Serializable
sealed interface ScElement {
    fun code(writer: ScWriter)
}

val ScElement.code: String
    get() {
        val writer = StringWriter()
        val scWriter = ScWriter(writer)
        code(scWriter)
        return writer.toString()
    }

@Serializable
@EditorInterface(ScExprEditor::class)
@UseEditor(ScExprExpander::class)
@EditableList(serializable = true)
sealed interface ScExpr : ScElement

@Serializable
abstract class SimpleScElement(val code: String) : ScElement {
    override fun code(writer: ScWriter) = writer.append(code)
}

@Serializable
@EditorInterface(LiteralEditor::class)
@UseEditor(LiteralExpander::class)
@EditableList(serializable = true)
sealed interface Literal : ScExpr {
    companion object : TokenType<Literal> {
        override fun compile(token: String): Literal {
            if (token.isEmpty()) return EmptyExpr
            token.toIntOrNull()?.let { value -> return IntegerLiteral(value) }
            token.toDoubleOrNull()?.let { value -> return DoubleLiteral(value) }
            if (token == "true") return BooleanLiteral(true)
            if (token == "false") return BooleanLiteral(false)
            if (token == "nil") return Nil
            return UnrecognizedToken(token)
        }
    }
}

sealed interface Invalid

data class UnrecognizedToken(val text: String) : Literal, Invalid {
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

@Serializable
data class IntegerLiteral(val value: Int) : Literal, SimpleScElement("$value")

@Serializable
data class DoubleLiteral(val value: Double) : Literal, SimpleScElement(value.format(4))

@Serializable
@Token(nodeType = Literal::class)
data class SymbolLiteral(val name: String) : Literal, SimpleScElement("'$name'")

@Serializable
@Token(nodeType = Literal::class)
data class StringLiteral(val value: String) : Literal, SimpleScElement("\"$value\"")

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class ArrayExpr(val elements: List<ScExpr>) : ScExpr {
    override fun code(writer: ScWriter) = with(writer) {
        append("[")
        appendList(elements, separator = ", ")
        append("]")
    }
}

@Serializable
@Compound(serializable = true)
@EditableList(serializable = true)
data class TupleElement(val key: Identifier, val value: ScExpr) : ScElement {
    override fun code(writer: ScWriter) {
        key.code(writer)
        writer.append(": ")
        value.code(writer)
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class TupleExpr(val elements: List<TupleElement>) : Literal {
    override fun code(writer: ScWriter) = with(writer) {
        append("(")
        appendList(elements, separator = ", ")
        append(")")
    }
}

@Serializable
@Compound(nodeType = Literal::class, serializable = true)
data class LiteralArray(val elements: List<Literal>) : Literal {
    override fun code(writer: ScWriter) = with(writer) {
        append("[")
        appendList(elements, separator = ", ")
        append("]")
    }
}

@Serializable
@Token(nodeType = ScExpr::class, serializable = true)
data class Identifier(val text: String) : SimpleScElement(text), ScExpr {
    companion object {
        fun isValid(token: String): Boolean {
            if (token.isEmpty()) return false
            if (!token.first().isLetterOrDigit() && token.first() != '~') return false
            return token.drop(1).all { c -> c.isLetterOrDigit() }
        }
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class VariableAccess(val name: Identifier) : ScExpr, SimpleScElement(name.text)

@Serializable
data class RawScExpr(val code: String) : ScExpr {
    override fun code(writer: ScWriter) = writer.append(code)
}

@Serializable
@Compound(serializable = true)
@EditableList(serializable = true)
data class Variable(val name: Identifier, val defaultValue: ScExpr = EmptyExpr) : ScElement {
    override fun code(writer: ScWriter) {
        name.code(writer)
        if (defaultValue != EmptyExpr) {
            writer.append(" = ")
            defaultValue.code(writer)
        }
    }
}

@Serializable
@Compound(serializable = true)
data class CodeBlock(val variables: List<Variable> = emptyList(), val statements: List<ScExpr>) {
    fun code(writer: ScWriter) = with(writer) {
        if (variables.isNotEmpty()) {
            this.append("var ")
            this.appendList(variables, separator = ", ")
            this.appendLine(";")
        }
        for (expr in statements) {
            expr.code(this)
            this.appendLine(";")
        }
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class CodeGroup(val block: CodeBlock) : ScExpr {
    override fun code(writer: ScWriter) {
        block.code(writer)
    }
}

@Serializable
@Compound
@EditableList
data class Parameter(val name: Identifier, val defaultValue: ScExpr = EmptyExpr) : ScElement {
    override fun code(writer: ScWriter) {
        name.code(writer)
        if (defaultValue != EmptyExpr) {
            writer.append(" = ")
            defaultValue.code(writer)
        }
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class ScFunction(val parameters: List<Parameter> = emptyList(), val body: CodeBlock) : ScExpr {
    override fun code(writer: ScWriter) = writer.appendBlock("") {
        if (parameters.isNotEmpty()) {
            append("arg ")
            appendList(parameters, separator = ", ")
            appendLine(";")
        }
        body.code(writer)
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class Assignment(val variable: Identifier, val expression: ScExpr) : ScExpr {
    override fun code(writer: ScWriter) {
        writer.append("${variable.text} = ")
        expression.code(writer)
    }
}

@Serializable
sealed interface Selector

@Serializable
@Token(serializable = true)
enum class Operator(val code: String) : Selector {
    Plus("+"), Minus("-"), Times("*"), Div("/"), Mod("%"), Exp("**"),
    Le("<"), Leq("<="), Gr(">"), Greq(">="),
    Eq("=="), Neq("!="),
    PlusPlus("++"),
    Unrecognized("<???>");

    override fun toString(): String = code

    companion object : TokenType<Operator>, ConfiguredCompleter<Any?, Operator>(CompletionStrategy.simple) {
        val map = Operator.values().associateBy { op -> op.code }

        override fun compile(token: String): Operator = map[token] ?: Unrecognized

        override fun completionPool(context: Any?): Collection<Operator> = values().asList()
    }
}

@Serializable
@Compound(serializable = true)
@EditableList(serializable = true)
data class Argument(val name: Identifier = Identifier(""), val value: ScExpr) : ScElement {
    override fun code(writer: ScWriter) {
        if (name.text != "") writer.append("${name.text}: ")
        value.code(writer)
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class MessageSend(val receiver: ScExpr, val method: Identifier, val arguments: List<Argument>) : ScExpr {
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
    MessageSend(this, Identifier(message), arguments.map { arg -> Argument(value = arg) })

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class OperatorExpr(val left: ScExpr, val operator: Operator, val right: ScExpr) : ScExpr {
    override fun code(writer: ScWriter) {
        left.code(writer)
        writer.append(" ${operator.code} ")
        right.code(writer)
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class NewObject(val className: Identifier, val arguments: List<Argument>) : ScExpr {
    override fun code(writer: ScWriter) = with(writer) {
        className.code(writer)
        append("(")
        appendList(arguments, ", ")
        append(")")
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class AccessKey(val receiver: ScExpr, val keys: List<ScExpr>) : ScExpr {
    override fun code(writer: ScWriter) = with(writer) {
        receiver.code(writer)
        append("[")
        appendList(keys, ", ")
        append("]")
    }
}

@Serializable
@Compound(nodeType = ScExpr::class, serializable = true)
data class SpreadArray(val array: ScExpr) : ScExpr {
    override fun code(writer: ScWriter) {
        writer.append("*")
        array.code(writer)
    }
}