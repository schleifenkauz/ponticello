@file:Suppress("UNCHECKED_CAST")

package ponticello.ui.registry

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import javafx.event.Event
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import ponticello.model.registry.NamedObject
import ponticello.model.registry.NamedObject.Companion.NO_NAME
import ponticello.model.registry.ObjectList
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

interface ObjectListDisplayConfig<O : Any> {
    val boxStyle: Array<String> get() = arrayOf("object-box")

    val listStyle: Array<String> get() = arrayOf("object-list")

    val enableSelection: Boolean get() = true

    val hideWhileDragging: Boolean get() = false

    val enableAddObjectButton: Boolean get() = false

    val centerAddObjectButton: Boolean get() = false

    val buttonStyle: String get() = "medium-icon-button"

    val inlineOrientation: Orientation get() = Orientation.VERTICAL

    val supportedModes: Collection<ObjectListView.DisplayMode>
        get() = setOf(ObjectListView.DisplayMode.Inline(collapsable = false))

    val autoSelectNewObjects: Boolean get() = true

    val addSpaceBeforeActionBar: Boolean get() = true

    val showDragHandle: Boolean get() = dataFormat != null

    val dataFormat: DataFormat? get() = null

    fun getHeaderContent(obj: O): List<Node> = emptyList()

    fun detailWindowIcon(obj: O): Ikon = MaterialDesignE.EYE

    fun getActions(box: ObjectBox<O>): List<ContextualizedAction> = emptyList()

    fun getContent(obj: O, mode: ObjectListView.DisplayMode): Parent? = null

    fun configureBox(box: ObjectBox<O>, currentMode: ObjectListView.DisplayMode) {}

    fun configureDragboard(obj: O, dragboard: Dragboard) {}

    fun configureSubWindow(window: SubWindow, obj: O) {}

    fun getDragTarget(box: ObjectBox<O>): Node = box.nameLabel ?: box.header

    fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> =
        if (dragboard.hasContent(dataFormat)) arrayOf(TransferMode.MOVE)
        else emptyArray()

    fun getDroppedObject(ev: DragEvent): O? = null

    fun canDelete(obj: O): Boolean = !(obj is NamedObject && !obj.canDelete)

    fun canCopy(obj: O): Boolean = obj is NamedObject && obj.canCopy

    fun copy(obj: O): O =
        if (obj !is NamedObject) throw UnsupportedOperationException("Cannot copy $obj")
        else if (!obj.canCopy) throw UnsupportedOperationException("Cannot copy $obj")
        else obj.copy() as O

    fun dropObject(obj: O, idx: Int, list: ObjectList<O>) {
        list.add(obj, idx)
    }

    fun getDefaultDisplayName(obj: O): ReactiveString = reactiveValue(NO_NAME)

    fun createNewObject(ev: Event?): O? = null

    fun createNewObject(name: String, ev: Event?): O? = null

    fun onSelected(obj: O) {}

    fun onDeselected(obj: O) {}

    fun onRemoved(obj: O) {}

    fun boxLayout(obj: O, header: Region, content: Node?): Node =
        if (content != null) VBox(header, content)
        else header

    fun collapsedLayout(box: ObjectBox<O>, header: Region, content: Parent?): Node = boxLayout(box.obj, header, content)
}