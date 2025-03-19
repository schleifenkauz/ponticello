package xenakis.ui.registry

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.actions.registerShortcuts
import javafx.geometry.Orientation.*
import javafx.scene.control.ScrollPane
import javafx.scene.input.Clipboard
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
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
    var autoResizeScene = false

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
    private val boxes = config.items.mapTo(mutableListOf()) { obj -> ObjectBox(this, obj) }
    private var selectedBox: ObjectBox<O>? = null

    private var filter: (O) -> Boolean = { true }

    val displayedBoxes get() = boxes.filter { b -> b.layout in layout.children }

    init {
        isFitToWidth = true
        layoutBoxes()
        registerShortcuts(listActions<O>().withContext(this))
        content = layout
    }

    fun add(idx: Int, obj: O) {
        val box = ObjectBox(this, obj)
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
        layout.children.setAll(boxes.filter { box -> filter(box.obj) }.map { b -> b.layout })
        if (autoResizeScene && scene != null) scene.window.sizeToScene()
    }

    private fun select(box: ObjectBox<O>) {
        selectedBox?.layout?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
        selectedBox = box
        box.layout.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
    }

    fun select(idx: Int) {
        select(boxes[idx])
    }

    fun select(obj: O) {
        val box = boxes.find { b -> b.obj == obj } ?: error("No box for $obj")
        select(box)
    }

    private fun navigate(deltaIdx: Int) {
        val displayedBoxes = layout.children
        val target = if (selectedBox != null) {
            val idx = layout.children.indexOf(selectedBox!!.layout)
            val newIdx = (idx + deltaIdx).coerceIn(layout.children.indices)
            displayedBoxes[newIdx]
        } else if (displayedBoxes.isNotEmpty()) {
            if (deltaIdx > 0) displayedBoxes.last() else displayedBoxes.first()
        } else return
        val nextBox = this.boxes.find { b -> b.layout == target } ?: error("No box for $target")
        select(nextBox)
    }

    private fun moveSelected(deltaIdx: Int) {
        val selected = selectedBox ?: return
        val idx = layout.children.indexOf(selected.layout)
        val newIdx = idx + deltaIdx
        if (newIdx !in layout.children.indices) return
        config.deleteObject(selected.obj)
        config.addObject(selected.obj, idx)
    }

    private fun copySelected() {
        val selected = selectedBox ?: return
        val format = config.dataFormat(selected.obj)
        val content = mapOf(format to selected.obj)
        val clipboard = Clipboard.getSystemClipboard()
        clipboard.setContent(content)
    }

    private fun renameSelected() {
        val selected = selectedBox ?: return
        selected.nameControl?.startEdit()
    }

    class ObjectBox<O : NamedObject>(val parent: ObjectBoxList<O>, val obj: O) {
        val config get() = parent.config

        val nameControl = if (obj is RenamableObject) NameControl(obj) else null

        private val nameDisplay =
            nameControl ?: HBox(label(obj.name).styleClass("name-field")).styleClass("name")
        private val content = config.getContent(obj)
        private val actionBar = ActionBar(
            config.getActions(obj) + parent.objectActions.withContext(obj),
            config.buttonStyle
        )

        val layout: Pane = when (config.orientation) {
            HORIZONTAL -> HBox(nameDisplay, *content.toTypedArray(), infiniteSpace(), actionBar)
            VERTICAL -> VBox(
                HBox(nameDisplay, infiniteSpace(), actionBar),
                *content.toTypedArray()
            )
        }.styleClass("object-box")

        init {
            layout.addEventFilter(MouseEvent.MOUSE_CLICKED) { parent.select(this) }
            if (config.enableReordering) setupReordering()
            if (config.dataFormat(obj) != null) setupDragging()
        }

        private fun setupDragging() {
            val btn = actionBar.getButton(parent.objectActions.getAction("Grabber"))
            btn.setOnDragDetected { ev ->
                val db = btn.startDragAndDrop(TransferMode.COPY)
                db.setContent(mapOf(config.dataFormat(obj) to obj.name.now))
                ev.consume()
            }
        }

        private fun setupReordering() {
            actionBar.getButton(parent.objectActions.getAction("Reorder")).setupDragging(
                onPressed = { layout.viewOrder = 100.0 },
                relocateBy = { _, _, _, _, dy -> layout.translateY = dy },
                onReleased = {
                    layout.viewOrder = 0.0
                    var idx = parent.boxes.binarySearchBy(layout.layoutY + layout.translateY) { b -> b.layout.layoutY }
                    if (idx < 0) idx = -(idx + 1)
                    val oldIndex = layout.children.indexOf(this.layout)
                    if (idx != oldIndex) {
                        config.deleteObject(obj)
                        config.addObject(obj, idx)
                    }
                    layout.translateY = 0.0
                }
            )
        }
    }

    companion object {
        private fun <O : NamedObject> listActions() = collectActions<ObjectBoxList<O>> {
            addAction("Rename selected") {
                shortcut("F2")
                executes { list -> list.renameSelected() }
            }
            addAction("Delete selected") {
                shortcut("Ctrl+DELETE")
                executes { list ->
                    val selected = list.selectedBox?.obj ?: return@executes
                    if (selected.canDelete) list.config.deleteObject(selected)
                }
            }
            addAction("Select previous") {
                shortcut("UP")
                executes { list -> list.navigate(-1) }
            }
            addAction("Select next") {
                shortcut("DOWN")
                executes { list -> list.navigate(+1) }
            }
            addAction("Move up") {
                shortcut("Ctrl+UP")
                applicableIf { list -> reactiveValue(list.config.enableReordering) }
                executes { list -> list.moveSelected(-1) }
            }
            addAction("Move down") {
                shortcut("Ctrl+DOWN")
                applicableIf { list -> reactiveValue(list.config.enableReordering) }
                executes { list -> list.moveSelected(+1) }
            }
            addAction("Copy item") {
                shortcut("Ctrl+C")
                executes { list -> list.copySelected() }
            }
        }
    }
}