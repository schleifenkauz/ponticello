package xenakis.ui.registry

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
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

class ObjectBoxList<O : NamedObject>(private val source: ObjectBoxSource<O>) : ScrollPane() {
    var autoResizeScene = false

    private val objectActions = collectActions<O> {
        addAction("Delete object") {
            icon(Material2AL.DELETE)
            shortcuts("Ctrl+DELETE")
            applicableIf { obj -> reactiveValue(obj.canDelete) }
            executes { obj -> source.deleteObject(obj) }
        }
        addAction("Grabber") {
            icon(MaterialDesignC.CURSOR_POINTER)
            applicableIf { obj -> reactiveValue(source.dataFormat(obj) != null) }
        }
        addAction("Reorder") {
            icon(MaterialDesignR.REORDER_HORIZONTAL)
            applicableIf { reactiveValue(source.enableReordering) }
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
    private val boxesCache = mutableMapOf<O, ObjectBox<O>>()
    private fun getBox(obj: O) = boxesCache.getOrPut(obj) { ObjectBox(this, obj) }
    private val boxes = mutableListOf<ObjectBox<O>>()
    private var selectedBox: ObjectBox<O>? = null

    private var filter: (O) -> Boolean = { true }

    val actions = listActions<O>().withContext(this)

    val children get() = boxes.map { b -> b.layout }

    init {
        isFitToWidth = true
        setupInitialBoxes()
        content = layout
    }

    private fun setupInitialBoxes() {
        for (obj in source.items) {
            if (filter(obj)) {
                val box = getBox(obj)
                boxes.add(box)
                layout.children.add(box.layout)
            }
        }
    }

    fun add(idx: Int, obj: O) {
        if (!filter(obj)) return
        val sourceIndices = mutableListOf<Int>()
        var i = 0
        val items = source.items
        for (b in boxes) {
            while (b != items[i]) i++
            sourceIndices.add(i)
        }
        var j = -(sourceIndices.binarySearch(idx) + 1)
        check(j >= 0)
        val box = getBox(obj)
        boxes.add(j, box)
        layout.children.add(j, box.layout)
    }

    fun remove(obj: O) {
        val box = getBox(obj)
        boxes.remove(box)
        layout.children.remove(box.layout)
    }

    fun setFilter(predicate: (O) -> Boolean) {
        filter = predicate
        refilter()
    }

    fun refilter() {
        boxes.clear()
        source.items.filter(filter).mapTo(boxes) { obj -> ObjectBox(this, obj) }
        layout.children.setAll(boxes.map { box -> box.layout })
        if (boxes.isNotEmpty()) select(0)
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
        if (selectedBox != null) {
            val idx = boxes.indexOf(selectedBox!!)
            val newIdx = (idx + deltaIdx).coerceIn(boxes.indices)
            select(boxes[newIdx])
        } else if (boxes.isNotEmpty()) {
            if (deltaIdx < 0) select(boxes.last())
            else select(boxes.first())
        }
    }

    private fun moveSelected(deltaIdx: Int) {
        val selected = selectedBox ?: return
        val idx = boxes.indexOf(selected)
        val newIdx = idx + deltaIdx
        if (newIdx !in layout.children.indices) return
        source.deleteObject(selected.obj)
        source.addObject(selected.obj, idx)
    }

    private fun copySelected() {
        val selected = selectedBox ?: return
        val format = source.dataFormat(selected.obj)
        val content = mapOf(format to selected.obj)
        val clipboard = Clipboard.getSystemClipboard()
        clipboard.setContent(content)
    }

    private fun renameSelected() {
        val selected = selectedBox ?: return
        selected.nameControl?.startEdit()
    }

    class ObjectBox<O : NamedObject>(val parent: ObjectBoxList<O>, val obj: O) {
        val config get() = parent.source

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
        internal fun <O : NamedObject> listActions() = collectActions<ObjectBoxList<O>> {
            addAction("Rename selected") {
                shortcut("F2")
                executes { list -> list.renameSelected() }
            }
            addAction("Delete selected") {
                shortcut("Ctrl+DELETE")
                executes { list ->
                    val selected = list.selectedBox?.obj ?: return@executes
                    if (selected.canDelete) list.source.deleteObject(selected)
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
                applicableIf { list -> reactiveValue(list.source.enableReordering) }
                executes { list -> list.moveSelected(-1) }
            }
            addAction("Move down") {
                shortcut("Ctrl+DOWN")
                applicableIf { list -> reactiveValue(list.source.enableReordering) }
                executes { list -> list.moveSelected(+1) }
            }
            addAction("Copy item") {
                shortcut("Ctrl+C")
                executes { list -> list.copySelected() }
            }
        }
    }
}