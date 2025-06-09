package ponticello.ui.live

import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.event.Event
import javafx.geometry.Orientation
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.model.live.LiveTaskObject
import ponticello.model.live.LiveTaskRegistry
import ponticello.model.obj.withName
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.misc.CodePane
import ponticello.ui.registry.ObjectListView.DisplayMode

class LiveTaskRegistryPane(registry: LiveTaskRegistry) : LiveObjectRegistryPane<LiveTaskObject>(registry) {
    override val title: String
        get() = "LiveTasks"

    override val icon: Ikon
        get() = MaterialDesignP.PROGRESS_QUESTION //TODO

    override val supportedModes: Set<DisplayMode>
        get() = DisplayMode.all

    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override fun defaultState(): ToolPaneState = ToolPaneState.docked(ToolPaneState.Side.RIGHT)

    override fun dataFormat(obj: LiveTaskObject): DataFormat = LiveTaskObject.DATA_FORMAT

    override fun createNewObject(name: String, ev: Event?): LiveTaskObject = LiveTaskObject(
        EditorRoot(CodeBlockEditor().defaultState())
    ).withName(name)

    override fun getContent(obj: LiveTaskObject, mode: DisplayMode): Parent {
        val actions =
            if (mode == DisplayMode.SubWindow) LiveObjectRegistryPane.actions.withContext(obj)
            else emptyList()
        return CodePane(obj.code, actions, ownWindow = mode == DisplayMode.SubWindow)
    }
}