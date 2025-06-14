package ponticello.ui.dock

import kotlinx.serialization.Serializable
import ponticello.model.flow.AudioFlows
import ponticello.ui.registry.BufferRegistryPane
import ponticello.ui.registry.BusRegistryPane
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
sealed class SearchableToolPaneState : ToolPaneState() {
    var displayMode: ObjectListView.DisplayMode? = null

    var expandedBoxes = emptyList<Int>()

    companion object {
        val docked get() = RegularSearchableToolPaneState().apply { mode = ToolPaneMode.Docked }
        val window get() = RegularSearchableToolPaneState().apply { mode = ToolPaneMode.Window }
        val floating get() = RegularSearchableToolPaneState().apply { mode = ToolPaneMode.Floating }
    }
}

@Serializable
class RegularSearchableToolPaneState : SearchableToolPaneState()

@Serializable
class FlowPaneState: SearchableToolPaneState() {
    var expandedFlows = emptyList<Int>()

    init {
        mode = ToolPaneMode.Docked
    }
}

@Serializable
class MixerPaneState: ToolPaneState() {
    var flowReference: AudioFlows.FlowReference? = null
}

@Serializable
class BusRegistryPaneState private constructor(): SearchableToolPaneState() {
    lateinit var filter: BusRegistryPane.BusTypeFilter

    companion object {
        fun default() = BusRegistryPaneState().apply {
            mode = ToolPaneMode.Docked
            filter = BusRegistryPane.BusTypeFilter.All
        }
    }
}

@Serializable
class BufferRegistryPaneState private constructor(): SearchableToolPaneState() {
    lateinit var filter: BufferRegistryPane.BufferTypeFilter

    companion object {
        fun default() = BufferRegistryPaneState().apply {
            mode = ToolPaneMode.Docked
            filter = BufferRegistryPane.BufferTypeFilter.All
        }
    }
}
