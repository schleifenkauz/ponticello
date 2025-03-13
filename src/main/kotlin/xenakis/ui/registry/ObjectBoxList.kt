package xenakis.ui.registry

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import javafx.scene.control.ScrollPane
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable
import xenakis.model.obj.RenamableObject
import xenakis.model.registry.NamedObject
import xenakis.ui.controls.NameControl

class ObjectBoxList<O : NamedObject>(items: Collection<O>, val type: ObjectBoxType<O>) : ScrollPane() {
    private val layout = VBox()
    private val boxes = items.mapTo(mutableListOf(), ::ObjectBox)
    private val draggingEnabled = reactiveVariable(false)
    private val reorderingEnabled = reactiveVariable(false)
    private var selectedBox: ObjectBox? = null

    private var filter: (O) -> Boolean = { true }

    private val actions = collectActions<ObjectBoxList<O>> {
        addAction("Delete selected") {
            shortcut("Ctrl+DELETE")
            executes { pane ->
                remove()
            }
        }
        addAction("Navigate") {
            shortcut("Shift?+TAB")
            executes { list, ev ->
                val boxes = list.boxes
                if (list.selectedBox != null) {
                    val idx = boxes.indexOf(list.selectedBox)
                    val newIdx = when {
                        idx == 0 && ev.isShiftDown() -> boxes.indices.last
                        idx == boxes.indices.last && !ev.isShiftDown() -> 0
                        ev.isShiftDown() -> idx - 1
                        else -> idx + 1
                    }
                    list.select(boxes[newIdx])
                } else if (boxes.isNotEmpty()) {
                    list.select(if (ev.isShiftDown()) boxes.last() else boxes.first())
                }
            }
        }
    }

    private val objectActions = collectActions<O> {
        addAction("Delete object") {
            icon(Material2AL.DELETE)
            shortcuts("Ctrl+DELETE")
            applicableIf { obj -> reactiveValue(obj.canDelete) }
            executes { obj -> type.deleteObject(obj) }
        }
        addAction("Grabber") {
            icon(MaterialDesignC.CURSOR_POINTER)
            applicableIf { draggingEnabled }
        }
        addAction("Reorder") {
            icon(MaterialDesignR.REORDER_HORIZONTAL)
            applicableIf { reorderingEnabled }
        }
    }

    init {
        isFitToWidth = true
        layoutBoxes()
    }

    fun add(idx: Int, obj: O) {
        val box = ObjectBox(obj)
        boxes.add(idx, box)
        layoutBoxes()
    }

    fun remove(obj: O) {
        boxes.removeIf { b -> b.obj == obj }
        layoutBoxes()
    }

    fun setFilter(predicate: (O) -> Boolean) {
        filter = predicate
        layoutBoxes()
    }

    fun layoutBoxes() {
        layout.children.setAll(boxes.filter { box -> filter(box.obj) })
        if (scene != null) scene.window.sizeToScene()
    }

    private fun select(box: ObjectBox) {
        selectedBox?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
        selectedBox = box
        box.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
    }

    fun select(idx: Int) {
        select(boxes[idx])
    }

    fun select(obj: O) {
        val box = boxes.find { b -> b.obj == obj } ?: error("No box for $obj")
        select(box)
    }

    fun enableDragging(
        box: ObjectBoxList<O>.ObjectBox,
        dataFormat: DataFormat,
        extraConfig: Dragboard.() -> Unit = {}
    ) {
        draggingEnabled.set(true)
        val btn = box.actionBar.getButton(objectActions.getAction("Grabber"))
        btn.setOnDragDetected { ev ->
            val db = startDragAndDrop(TransferMode.COPY)
            db.setContent(mapOf(dataFormat to box.obj.name.now))
            db.extraConfig()
            ev.consume()
        }
    }

    fun enableReordering() {
        reorderingEnabled.set(true)
    }

    inner class ObjectBox(val obj: O) : HBox() {
        val nameDisplay =
            if (obj is RenamableObject) NameControl(obj)
            else HBox(label(obj.name).styleClass("name-field")).styleClass("name")
        private val content = type.getContent(obj)
        val actionBar = ActionBar(type.getActions(obj), buttonStyle = "medium-icon-button")

        init {
            styleClass("object-box")
            children.addAll(nameDisplay, *content.toTypedArray(), infiniteSpace(), actionBar)
            addEventFilter(MouseEvent.MOUSE_CLICKED) { select(this) }
            setupReordering()
        }

        private fun setupReordering() {
            actionBar.getButton(actions.getAction("Reorder")).setupDragging(
                onPressed = { viewOrder = 100.0 },
                relocateBy = { _, _, _, _, dy -> translateY = dy },
                onReleased = {
                    viewOrder = 0.0
                    var idx = boxes.binarySearchBy(layoutY + translateY) { b -> b.layoutY }
                    if (idx < 0) idx = -(idx + 1)
                    val oldIndex = layout.children.indexOf(this)
                    if (idx != oldIndex) {
                        remove(obj)
                        add(idx, obj)
                    }
                    translateY = 0.0
                }
            )
        }
    }
}