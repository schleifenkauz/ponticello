package ponticello.sc.client

import hextant.context.Context
import ponticello.sc.DisabledExpr
import ponticello.sc.ScElement
import java.io.StringWriter

class ScWriter(private val output: Appendable) : SuperColliderContext {
    private var indent = ""
    private var newLine = true

    val writer: ScWriter get() = this

    fun append(str: String) {
        if (newLine) {
            output.append(indent)
            newLine = false
        }
        output.append(str)
    }

    fun appendLine() {
        if (newLine) return
        output.appendLine()
        newLine = true
    }

    fun appendLine(str: String) {
        append(str)
        appendLine()
    }

    operator fun String.unaryPlus() {
        if (this.isBlank()) return
        append(this)
        appendLine(";")
    }

    fun appendList(list: Iterable<ScElement>, separator: String, context: Context) {
        for ((i, element) in list.withIndex()) {
            if (i != 0 && element !is DisabledExpr) append(separator)
            element.code(this, context)
        }
    }

    fun increaseIndent() {
        indent += "  "
    }

    fun decreaseIndent() {
        indent = indent.dropLast(2)
    }

    inline fun indented(block: ScWriter.() -> Unit) {
        increaseIndent()
        appendLine()
        this.block()
        appendLine()
        decreaseIndent()
    }

    inline fun appendBlock(s: String = "", endLine: Boolean = true, block: ScWriter.() -> Unit) {
        if (s.isNotEmpty()) {
            append(s)
            append(" ")
        }
        append("{")
        indented(block)
        append("}")
        if (endLine) appendLine(";")
    }

    inline fun appendGroup(block: ScWriter.() -> Unit) {
        append("(")
        indented(block)
        appendLine(")")
    }

    override fun run(command: String) {
        appendLine(command)
    }

    companion object {
        inline fun wrap(context: SuperColliderContext, block: ScWriter.() -> Unit) {
            if (context is ScWriter) return context.block()
            val writer = StringWriter()
            val wrapper = ScWriter(writer)
            wrapper.block()
            context.run(writer.toString())
        }
    }
}