package xenakis.model.flow

import hextant.codegen.Choice

@Choice(defaultValue = "FlowType.InOut")
enum class FlowType {
    In, Out, InOut;

    override fun toString(): String = when (this) {
        In -> "in"
        Out -> "out"
        InOut -> "eff"
    }

    companion object {
        val all = entries.toTypedArray()
    }
}