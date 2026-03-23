@file:Suppress("UNCHECKED_CAST")

package ponticello.ui.registry

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.action
import fxutils.drag.setupDropArea
import fxutils.prompt.PromptPlacement
import fxutils.prompt.nextToTarget
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.model.obj.NamedObject
import ponticello.model.obj.RenamableObject
import ponticello.model.obj.withName
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ObjectList
import ponticello.ui.controls.NamePrompt
import ponticello.ui.impl.getFrom
import reaktive.Reactive
import reaktive.value.fx.asObservableValue
import reaktive.value.now

interface ListDisplayConfig<O : Any> {
    val boxStyle: Array<String> get() = arrayOf("object-box")

    val listStyle: Array<String> get() = arrayOf("object-list")

    val enableSelection: Boolean get() = true

    val hideWhileDragging: Boolean get() = false

    val enableAddObjectButton: Boolean get() = false

    val centerAddObjectButton: Boolean get() = false

    val canCreateNewObject: Boolean get() = true

    val buttonStyle: String get() = "medium-icon-button"

    val nameDisplayWidth: Double get() = 150.0

    val inlineOrientation: Orientation get() = Orientation.VERTICAL

    val supportedModes: Collection<ObjectListView.DisplayMode>
        get() = setOf(ObjectListView.DisplayMode.Inline(collapsable = false))

    val autoSelectNewObjects: Boolean get() = true

    val addSpaceBeforeActionBar: Boolean get() = true

    val showDragHandle: Boolean get() = dataFormat != null

    val dataFormat: DataFormat? get() = null

    fun displayName(obj: O): Boolean = true

    fun filter(obj: O): Boolean = true

    fun getHeaderContent(obj: O): List<Node> = emptyList()

    fun detailWindowIcon(obj: O): Ikon = MaterialDesignE.EYE

    fun getActions(box: ObjectBox<O>): List<ContextualizedAction> = emptyList()

    fun getContent(obj: O, box: ObjectBox<O>): Parent? = null

    fun contentUpdate(obj: O): Reactive? = null

    fun configureBox(box: ObjectBox<O>, currentMode: ObjectListView.DisplayMode) {}

    fun createSeparatorNode(box: ObjectBox<O>): Node? = null

    fun emptyDisplay(view: ObjectListView<O>): Node {
        val objectType = plural(view.source.objectType)
        val emptyDisplay = VBox(Label("No $objectType to display")) styleClass "empty-display"
        emptyDisplay.centerChildren()
        emptyDisplay.setPadding(10.0)
        emptyDisplay.setupDropArea(view.emptyListDropHandler())
        return emptyDisplay
    }

    fun configureDragboard(obj: O, dragboard: Dragboard) {}

    fun configureSubWindow(window: SubWindow, obj: O) {}

    fun getDragTarget(box: ObjectBox<O>): Node = box.nameLabel ?: box.header

    fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> =
        if (dragboard.hasContent(dataFormat)) {
            if (canDuplicate) TransferMode.COPY_OR_MOVE
            else arrayOf(TransferMode.MOVE)
        } else emptyArray()

    fun getDroppedObjects(ev: DragEvent, targetView: ObjectListView<O>): List<O> {
        val format = dataFormat
        if (format != null && ev.dragboard.hasContent(format)) {
            val source = targetView.source
            if (source !is NamedObjectList<*>) return emptyList()
            val obj = ev.dragboard.getFrom(source, format) ?: return emptyList()
            return if (ev.acceptedTransferMode == TransferMode.COPY) {
                val newName = NamePrompt(source, "Name for copy", obj.name.now + "_copy")
                    .showDialog(ev.nextToTarget()) ?: return emptyList()
                listOf(duplicate(obj as O, newName))
            } else listOf(obj as O)
        }
        return emptyList()
    }

    fun canDelete(obj: O): Boolean = !(obj is NamedObject && !obj.canDelete)

    val canDuplicate: Boolean get() = false

    fun duplicate(obj: O, newName: String): O =
        if (obj !is RenamableObject) throw UnsupportedOperationException("Cannot copy $obj")
        else obj.copy().withName(newName) as O

    fun dropObject(obj: O, idx: Int, list: ObjectList<O>, from: ObjectList<O>?) {
        from?.remove(obj)
        list.add(obj, idx)
    }

    fun createNewObject(promptPlacement: PromptPlacement, list: ObjectList<O>): O? {
        if (list !is NamedObjectList) return null
        val name = NamePrompt(list, "Name for new ${list.objectType}", initialName = "")
            .showDialog(promptPlacement) ?: return null
        return createNewObject(name, promptPlacement)
    }

    fun createNewObject(name: String, promptPlacement: PromptPlacement?): O? = null

    fun defaultInsertionIndex(source: List<O>) = source.size

    fun onCreated(obj: O, box: ObjectBox<O>) {}

    fun onSelected(obj: O) {}

    fun onDeselected(obj: O) {}

    fun onRemoved(obj: O) {}

    fun expandedLayout(box: ObjectBox<O>): Node =
        if (box.content != null) VBox(box.header, box.content)
        else box.header

    fun collapsedLayout(box: ObjectBox<O>): Node = when (inlineOrientation) {
        Orientation.VERTICAL -> box.header
        Orientation.HORIZONTAL -> {
            val obj = box.obj
            val nameLabel = Text()
            if (obj is NamedObject) {
                nameLabel.textProperty().bind(obj.name.asObservableValue())
                val namePane = makeVerticalLabel(nameLabel).alwaysVGrow()
                val actions = listOf(expandAction.withContext(box)) + getActions(box).reversed()
                val actionBar = ActionBar(actions, "small-icon-button")
                actionBar.setOrientation(Orientation.VERTICAL)
                nameLabel.setOnMouseClicked { ev ->
                    if (ev.clickCount == 2) {
                        box.toggleExpanded()
                    }
                }
                val layout = VBox(actionBar, namePane)
                box.setupDragging(dragTarget = layout)
                layout.pad(3.0)
            } else box.header
        }
    }

    fun expandNewItem(obj: O): Boolean = true

    companion object {
        private val expandAction = action<ObjectBox<*>>("Expand") {
            icon(MaterialDesignC.CHEVRON_RIGHT)
            executes { box -> box.setExpanded(true) }
        }

        val insertObjectAction = action<ObjectBox<*>>("Insert object") {
            icon(MaterialDesignP.PLUS)
            executes { box, ev ->
                val listView = box.getParent<ObjectListView<*>>() ?: return@executes
                val idx = listView.source.indexOf(box.obj) + 1
                val placement = ev?.nextToTarget() ?: PromptPlacement.RelativeTo(box)
                listView.addObject(placement, idx)
            }
        }

        val addObjectAction = action<ObjectListView<*>>("Add object") {
            icon(MaterialDesignP.PLUS)
            executes { view, ev ->
                val placement = ev?.nextToTarget() ?: PromptPlacement.RelativeTo(view)
                view.addObject(placement, idx = 0)
            }
        }
    }
}