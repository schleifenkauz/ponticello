package xenakis.model.flow

import hextant.codegen.Choice

@Choice(defaultValue = "FlowType.InOut")
enum class FlowType {
    In, Out, InOut;

    companion object {
        val all = entries.toTypedArray()
    }
}