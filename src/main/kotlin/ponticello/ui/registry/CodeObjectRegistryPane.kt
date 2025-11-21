package ponticello.ui.registry

import hextant.serial.EditorRoot
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.ScrollPane
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import ponticello.model.obj.RenamableObject
import ponticello.model.project.ComponentSerializer
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.editor.ScExprEditor
import ponticello.ui.misc.CodePane
import ponticello.ui.registry.ObjectListView.DisplayMode

abstract class CodeObjectRegistryPane<O : RenamableObject>(
    registry: ObjectRegistry<O>, serializer: ComponentSerializer<*>?
) : ObjectRegistryPane<O>(registry, serializer) {
    protected abstract fun getEditorRoot(obj: O): EditorRoot<out ScExprEditor<*>>

    override fun detailWindowIcon(obj: O): Ikon = Material2AL.CODE

    override fun getContent(obj: O, box: ObjectBox<O>): Parent =
        when (box.currentMode) {
            DisplayMode.SubWindow -> {
                CodePane(
                    getEditorRoot(obj), extraActions = emptyList(),
                    ownWindow = true, actionBarAlignment = Pos.BOTTOM_RIGHT
                )
            }

            else -> ScrollPane(getEditorRoot(obj).control)
        }

    override fun onCreated(obj: O, box: ObjectBox<O>) {
        if (box.currentMode == DisplayMode.Collapsable) {
            getEditorRoot(obj).control.receiveFocus()
        }
    }
}