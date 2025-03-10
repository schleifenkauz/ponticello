package xenakis.model.flow

import hextant.codegen.Choice

@Choice(defaultValue = "FlowType.Out")
enum class FlowType {
    In, Out;

    override fun toString(): String = when (this) {
        In -> "in"
        Out -> "out"
    }

    companion object {
        val all = entries.toTypedArray()
    }
}