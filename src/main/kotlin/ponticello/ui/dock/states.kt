package ponticello.ui.dock

import kotlinx.serialization.Serializable
import ponticello.ui.registry.ObjectListView

@Serializable
sealed class ToolPaneState {
    var uid: Int = -1
    lateinit var mode: ToolPaneMode
    var windowBounds: WindowBounds? = null
    var isShowing: Boolean = false
    var isExclusive: Boolean = false

    companion object {
        val docked get() = RegularToolPaneState().apply { mode = ToolPaneMode.Docked }
        val window get() = RegularToolPaneState().apply { mode = ToolPaneMode.Window }
        val floating get() = RegularToolPaneState().apply { mode = ToolPaneMode.Floating }
    }
}

@Serializable
class RegularToolPaneState : ToolPaneState()

@Serializable
class SearchableToolPaneState : ToolPaneState() {
    var displayMode: ObjectListView.DisplayMode? = null
}