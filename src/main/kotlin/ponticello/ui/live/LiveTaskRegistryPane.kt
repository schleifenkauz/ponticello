package ponticello.ui.live

import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.event.Event
import javafx.geometry.Orientation
import javafx.scene.Parent
import ponticello.model.live.LiveTaskObject
import ponticello.model.live.LiveTaskRegistry
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.misc.CodePane
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.reactiveVariable

class LiveTaskRegistryPane(registry: LiveTaskRegistry) : LiveObjectRegistryPane<LiveTaskObject>(registry) {
    init {
        setup()
    }

    override val supportedModes: Set<DisplayMode>
        get() = DisplayMode.all

    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override fun createNewObject(name: String, ev: Event?): LiveTaskObject = LiveTaskObject(
        reactiveVariable(name),
        EditorRoot(CodeBlockEditor().defaultState())
    )

    override fun getContent(obj: LiveTaskObject, mode: DisplayMode): Parent {
        val actions =
            if (mode == DisplayMode.SubWindow) LiveObjectRegistryPane.actions.withContext(obj)
            else emptyList()
        return CodePane(obj.code, actions, ownWindow = mode == DisplayMode.SubWindow)
    }
}