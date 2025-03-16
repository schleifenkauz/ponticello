package xenakis.model.flow

import hextant.codegen.Choice
import kotlinx.serialization.Serializable

@Serializable
@Choice
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