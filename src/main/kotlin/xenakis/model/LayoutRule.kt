package xenakis.model

import kotlinx.serialization.Serializable

@Serializable
data class LayoutRule(val type: Type, val objectNames: MutableSet<String>) {
    @Serializable
    enum class Type { Vertical, Horizontal, Omnidirectional }
}