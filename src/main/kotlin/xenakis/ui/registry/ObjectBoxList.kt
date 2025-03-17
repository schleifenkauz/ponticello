package xenakis.ui.registry

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import fxutils.actions.registerShortcuts
import javafx.scene.control.ScrollPane
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.obj.RenamableObject
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.controls.NameControl
import xenakis.ui.controls.NamePrompt

class ObjectBoxList<O : NamedObject>(
    private val config: ObjectBoxSource<O>
) : ScrollPane() {
    private val objectActions = collectActions<O> {
        addAction("Delete object") {
            icon(Material2AL.DELETE)
            shortcuts("Ctrl+DELETE")
            applicableIf { obj -> reactiveValue(obj.canDelete) }
            executes { obj -> config.deleteObject(obj) }
        }
        addAction("Grabber") {
            icon(MaterialDesignC.CURSOR_POINTER)
            applicableIf { obj -> reactiveValue(config.dataFormat(obj) != null) }
        }
        addAction("Reorder") {
            icon(MaterialDesignR.REORDER_HORIZONTAL)
            applicableIf { reactiveValue(config.enableReordering) }
        }
        @Suppress("UNCHECKED_CAST")
        addAction("Duplicate object") {
            applicableIf { obj -> reactiveValue(obj.canCopy && obj.registry != null) }
            executes { obj, ev ->
                description { o -> reactiveValue(o.registry?.objectType ?: "object") }
                val registry = obj.registry as ObjectRegistry<O>
                val initialName = obj.name.now + "_copy"
                val name = NamePrompt(registry, "Name for new duplicate instrument", initialName)
                    .showDialog(ev) ?: return@executes
                val copy = obj.copy(name) as O
                registry.add(copy, registry.indexOf(obj))
            }
        }
    }

    private val layout = VBox()
    private val boxes = config.items.mapTo(mutableListOf(), ::ObjectBox)
    private var selectedBox: ObjectBox? = null

    private var filter: (O) -> Boolean = { true }

    init {
        isFitToWidth = true
        layoutBoxes()
        registerShortcuts(listActions<O>().withContext(this))
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

    inner class ObjectBox(val obj: O) : HBox() {
        private val nameDisplay =
            if (obj is RenamableObject) NameControl(obj)
            else HBox(label(obj.name).styleClass("name-field")).styleClass("name")
        private val content = config.getContent(obj)
        private val actionBar = ActionBar(
            config.getActions(obj) + objectActions.withContext(obj),
            buttonStyle = "medium-icon-button"
        )

        init {
            styleClass("object-box")
            children.addAll(nameDisplay, *content.toTypedArray(), infiniteSpace(), actionBar)
            addEventFilter(MouseEvent.MOUSE_CLICKED) { select(this) }
            if (config.enableReordering) setupReordering()
            if (config.dataFormat(obj) != null) setupDragging()
        }

        private fun setupDragging() {
            val btn = actionBar.getButton(objectActions.getAction("Grabber"))
            btn.setOnDragDetected { ev ->
                val db = startDragAndDrop(TransferMode.COPY)
                db.setContent(mapOf(config.dataFormat(obj) to obj.name.now))
                ev.consume()
            }
        }

        private fun setupReordering() {
            actionBar.getButton(objectActions.getAction("Reorder")).setupDragging(
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

    companion object {
        private fun <O : NamedObject> listActions() = collectActions<ObjectBoxList<O>> {
            addAction("Delete selected") {
                shortcut("Ctrl+DELETE")
                executes { list ->
                    val selected = list.selectedBox?.obj ?: return@executes
                    if (selected.canDelete) list.config.deleteObject(selected)
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
    }
}