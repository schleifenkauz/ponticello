package ponticello.ui.live

import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.event.Event
import javafx.geometry.Orientation
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignI
import ponticello.model.live.LiveTaskObject
import ponticello.model.live.LiveTaskRegistry
import ponticello.model.obj.withName
import ponticello.model.project.LIVE_TASKS
import ponticello.model.project.PonticelloProject
import ponticello.model.project.get
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.dock.SearchableToolPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.misc.CodePane
import ponticello.ui.registry.ObjectListView.DisplayMode

class LiveTaskRegistryPane(registry: LiveTaskRegistry) : LiveObjectRegistryPane<LiveTaskObject>(registry) {
    override val type: Type
        get() = LiveTaskRegistryPane

    override val supportedModes: Set<DisplayMode>
        get() = DisplayMode.all

    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override fun defaultState(): ToolPaneState = SearchableToolPaneState.docked

    override val dataFormat: DataFormat
        get() = LiveTaskObject.DATA_FORMAT

    override fun createNewObject(name: String, ev: Event?): LiveTaskObject = LiveTaskObject(
        EditorRoot(CodeBlockEditor().defaultState())
    ).withName(name)

    override fun getContent(obj: LiveTaskObject, mode: DisplayMode): Parent {
        val actions =
            if (mode == DisplayMode.SubWindow) LiveObjectRegistryPane.actions.withContext(obj)
            else emptyList()
        return CodePane(obj.code, actions, ownWindow = mode == DisplayMode.SubWindow)
    }

    companion object: Type(10, "LiveTasks") {
        override val icon: Ikon
            get() = MaterialDesignI.INFINITY

        override val defaultSide: Side
            get() = Side.RIGHT

        override fun createToolPane(project: PonticelloProject): ToolPane = LiveTaskRegistryPane(project[LIVE_TASKS])
    }
}