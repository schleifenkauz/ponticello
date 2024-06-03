package xenakis.impl

import xenakis.sc.ScElement

class ScWriter(private val output: Appendable) {
    private var indent = ""
    private var newLine = true

    val context = SuperColliderWriterContext(output)

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

    fun appendList(list: Iterable<ScElement>, separator: String) {
        for ((i, element) in list.withIndex()) {
            if (i != 0) append(separator)
            element.code(this)
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
        block()
        appendLine()
        decreaseIndent()
    }

    inline fun appendBlock(s: String = "", block: ScWriter.() -> Unit) {
        if (s.isNotEmpty()) {
            append(s)
            append(" ")
        }
        append("{")
        indented(block)
        append("}")
    }

    inline fun appendGroup(block: ScWriter.() -> Unit) {
        append("(")
        indented(block)
        appendLine(")")
    }
}