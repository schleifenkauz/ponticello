package ponticello.ui.dock

import kotlinx.serialization.Serializable
import ponticello.model.flow.MixerFlow
import ponticello.model.registry.ObjectReference
import ponticello.ui.misc.HelpBrowser
import ponticello.ui.registry.BufferRegistryPane
import ponticello.ui.registry.BusRegistryPane
import ponticello.ui.registry.ObjectListView

@Serializable
data class SideBarState(
    val side: Side,
    val dividerPosition: Double,
    val toolPanes: List<Int>,
    val dividerPositions: List<Double>,
    val isExpanded: Boolean,
)

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
sealed class ListToolPaneState : ToolPaneState() {
    var displayMode: ObjectListView.DisplayMode? = null

    var expandedBoxes = emptyList<Int>()

    companion object {
        val docked get() = RegularListToolPaneState().apply { mode = ToolPaneMode.Docked }
        val window get() = RegularListToolPaneState().apply { mode = ToolPaneMode.Window }
        val floating get() = RegularListToolPaneState().apply { mode = ToolPaneMode.Floating }
    }
}

@Serializable
class RegularListToolPaneState : ListToolPaneState()

@Serializable
class FlowPaneState private constructor(): ListToolPaneState() {
    var expandedFlows = emptyList<Int>()

    companion object {
        fun default() = FlowPaneState().apply {
            mode = ToolPaneMode.Docked
        }
    }
}

@Serializable
class MixerPaneState private constructor(): ToolPaneState() {
    var flowReference: ObjectReference<MixerFlow> = ObjectReference.none()
    var midiDeviceName: String? = null

    companion object {
        fun default() = MixerPaneState().apply {
            mode = ToolPaneMode.Docked
        }
    }
}

@Serializable
class BusRegistryPaneState private constructor() : ListToolPaneState() {
    lateinit var filter: BusRegistryPane.BusTypeFilter

    companion object {
        fun default() = BusRegistryPaneState().apply {
            mode = ToolPaneMode.Docked
            filter = BusRegistryPane.BusTypeFilter.All
        }
    }
}

@Serializable
class BufferRegistryPaneState private constructor() : ListToolPaneState() {
    lateinit var filter: BufferRegistryPane.BufferTypeFilter

    companion object {
        fun default() = BufferRegistryPaneState().apply {
            mode = ToolPaneMode.Docked
            filter = BufferRegistryPane.BufferTypeFilter.All
        }
    }
}

@Serializable
class BrowserPaneState private constructor() : ListToolPaneState() {
    lateinit var url: String

    companion object {
        fun default() = BrowserPaneState().apply {
            mode = ToolPaneMode.Docked
            url = HelpBrowser.DEFAULT_URL
        }
    }
}
