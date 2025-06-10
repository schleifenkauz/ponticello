package ponticello.ui.dock

import kotlinx.serialization.Serializable

@Serializable
enum class ToolPaneMode {
    Docked, Window, Floating;
}