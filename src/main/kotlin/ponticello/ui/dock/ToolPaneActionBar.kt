package ponticello.ui.dock

import fxutils.actions.ContextualizedAction
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard

class ToolPaneActionBar(
    private val appLayout: AppLayout,
    types: ToolPaneTypeList,
) : ReorderableActionBar<ToolPane.Type>("large-icon-button", types) {
    init {
        setup()
    }

    override val listStyle: Array<String>
        get() = arrayOf("dock-icons")

    override val dataFormat: DataFormat
        get() = ToolPane.Type.DATA_FORMAT

    override fun configureDragboard(obj: ToolPane.Type, dragboard: Dragboard) {
        dragboard.setContent(mapOf(dataFormat to obj.uid))
    }

    override fun getDroppedObject(ev: DragEvent): ToolPane.Type {
        val uid = ev.dragboard.getContent(dataFormat) as Int
        return AppLayout.toolPaneType(uid)
    }

    override fun getAction(obj: ToolPane.Type): ContextualizedAction {
        val toolPane = appLayout.getToolPane(obj) ?: error("ToolPane $obj not found")
        return ToolPaneAction(toolPane)
    }
}