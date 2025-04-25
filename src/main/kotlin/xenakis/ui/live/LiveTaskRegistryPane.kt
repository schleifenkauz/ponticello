package xenakis.ui.live

import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.geometry.Orientation
import javafx.scene.Parent
import reaktive.value.reactiveVariable
import xenakis.model.live.LiveTaskObject
import xenakis.model.live.LiveTaskRegistry
import xenakis.sc.editor.CodeBlockEditor
import xenakis.ui.misc.CodePane
import xenakis.ui.registry.NamedObjectListView.DisplayMode

class LiveTaskRegistryPane(registry: LiveTaskRegistry) : LiveObjectRegistryPane<LiveTaskObject>(registry) {
    init {
        setup()
    }

    override val supportedModes: Set<DisplayMode>
        get() = DisplayMode.all

    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override fun createNewObject(name: String): LiveTaskObject = LiveTaskObject(
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