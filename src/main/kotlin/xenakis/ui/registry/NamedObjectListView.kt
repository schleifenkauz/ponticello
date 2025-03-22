package xenakis.ui.registry

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import javafx.geometry.Orientation.HORIZONTAL
import javafx.geometry.Orientation.VERTICAL
import javafx.scene.control.ScrollPane
import javafx.scene.input.Clipboard
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.obj.RenamableObject
import xenakis.model.registry.NamedObject
import xenakis.model.registry.NamedObjectList
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.controls.NameControl
import xenakis.ui.controls.NamePrompt

class NamedObjectListView<O : NamedObject>(
    private val source: NamedObjectList<O>,
    private val config: ObjectBoxConfig<O>
) : ScrollPane(), NamedObjectList.Listener<O> {
    var autoResizeScene = false

    private val layout = VBox()
    private val boxesCache = mutableMapOf<O, ObjectBox<O>>()
    private fun getBox(obj: O) = boxesCache.getOrPut(obj) { ObjectBox(this, obj) }
    private val boxes = mutableListOf<ObjectBox<O>>()
    private var selectedBox: ObjectBox<O>? = null

    private var filter: (O) -> Boolean = { true }

    val actions = listActions<O>().withContext(this)

    private val objectActions = collectActions<O> {
        addAction("Delete object") {
            icon(Material2AL.DELETE)
            shortcuts("Ctrl+DELETE")
            applicableIf { obj -> reactiveValue(obj.canDelete) }
            executes { obj -> source.remove(obj) }
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


    val children get() = boxes.map { b -> b.layout }

    init {
        source.addListener(this, initialize = false)
        for (obj in source) {
            if (!filter(obj)) continue
            val box = getBox(obj)
            boxes.add(box)
            layout.children.add(box.layout)
        }
        isFitToWidth = true
        content = layout
    }

    override fun added(obj: O, idx: Int) {
        if (!filter(obj)) return
        val j = getInsertionIndex(idx)
        val box = getBox(obj)
        boxes.add(j, box)
        layout.children.add(j, box.layout)
    }

    private fun getInsertionIndex(idx: Int): Int {
        if (boxes.size == source.size - 1) return idx
        val sourceIndices = mutableListOf<Int>()
        var i = 0
        for (b in boxes) {
            while (i < source.size && b != source[i]) i++
            sourceIndices.add(i)
        }
        val j = sourceIndices.binarySearch(idx)
        check(j < 0) { "Already inserted box for index $idx" }
        return -(j + 1)
    }

    override fun removed(obj: O) {
        val box = getBox(obj)
        boxes.remove(box)
        layout.children.remove(box.layout)
    }

    override fun moved(obj: O, idx: Int) {
        removed(obj)
        added(obj, idx)
    }

    fun setFilter(predicate: (O) -> Boolean) {
        filter = predicate
        refilter()
    }

    fun refilter() {
        boxes.clear()
        source.filter(filter).mapTo(boxes) { obj -> ObjectBox(this, obj) }
        layout.children.setAll(boxes.map { box -> box.layout })
        if (boxes.isNotEmpty()) select(0)
        if (autoResizeScene && scene != null) scene.window.sizeToScene()
    }

    private fun select(box: ObjectBox<O>) {
        selectedBox?.layout?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
        selectedBox = box
        box.layout.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
        config.onSelected(box.obj)
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
        if (newIdx !in boxes.indices) return
        source.move(selected.obj, newIdx)
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

    class ObjectBox<O : NamedObject>(val parent: NamedObjectListView<O>, val obj: O) {
        val config get() = parent.config

        val nameControl = if (obj is RenamableObject) NameControl(obj) else null

        private val nameDisplay =
            nameControl ?: HBox(label(obj.name).styleClass("name-field")).styleClass("name")
        private val content = config.getContent(obj)
        private val actionBar = ActionBar(
            config.getActions(obj) + parent.objectActions.withContext(obj),
            config.buttonStyle
        )

        private var header: Region? = null

        val layout: Pane = when (config.orientation) {
            HORIZONTAL -> HBox(nameDisplay, *content.toTypedArray(), infiniteSpace(), actionBar)
            VERTICAL -> VBox(
                HBox(nameDisplay, infiniteSpace(), actionBar).also { header = it },
                *content.toTypedArray()
            )
        }.styleClass("object-box")

        init {
            layout.addEventFilter(MouseEvent.MOUSE_CLICKED) { parent.select(this) }
            if (config.enableReordering) setupReordering()
            if (config.dataFormat(obj) != null) setupDragging()
        }

        private fun setupDragging() {
            val dragTarget = header ?: layout
            dragTarget.setOnDragDetected { ev ->
                if (ev.isControlDown) {
                    val db = dragTarget.startDragAndDrop(TransferMode.COPY)
                    db.setContent(mapOf(config.dataFormat(obj) to obj.name.now))
                    ev.consume()
                }
            }
        }

        private fun setupReordering() {
            val dragTarget = header ?: layout
            dragTarget.setupDragging(
                onPressed = { layout.viewOrder = 100.0 },
                relocateBy = { _, _, _, _, dy -> layout.translateY = dy },
                onReleased = {
                    layout.viewOrder = 0.0
                    var idx = parent.boxes.binarySearchBy(layout.layoutY + layout.translateY) { b -> b.layout.layoutY }
                    if (idx < 0) idx = -(idx + 1)
                    val oldIndex = layout.children.indexOf(this.layout)
                    try {
                        if (idx != oldIndex) {
                            parent.source.move(obj, idx)
                        }
                    } finally {
                        layout.translateY = 0.0
                    }
                }
            )
        }
    }

    companion object {
        internal fun <O : NamedObject> listActions() = collectActions<NamedObjectListView<O>> {
            addAction("Rename selected") {
                shortcut("F2")
                executes { list -> list.renameSelected() }
            }
            addAction("Delete selected") {
                shortcut("Ctrl+DELETE")
                executes { list ->
                    val selected = list.selectedBox?.obj ?: return@executes
                    if (selected.canDelete) list.source.remove(selected)
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