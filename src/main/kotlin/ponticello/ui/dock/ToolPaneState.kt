package ponticello.ui.dock

import kotlinx.serialization.Serializable

@Serializable
data class ToolPaneState(
    val side: Side,
    val position: ToolPanePosition,
    val isShowing: Boolean = false,
    val isExclusive: Boolean = false
) {
    @Serializable
    enum class Side { LEFT, RIGHT, BOTTOM, TOP }

    companion object {
        fun docked(side: Side) = ToolPaneState(side, ToolPanePosition.docked)
    }
}