package xenakis.ui.registry

import fxutils.*
import fxutils.actions.Action
import fxutils.actions.collectActions
import javafx.geometry.Dimension2D
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.input.Clipboard
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import org.kordamp.ikonli.materialdesign2.MaterialDesignV
import reaktive.value.*
import reaktive.value.binding.notEqualTo
import xenakis.model.registry.NamedObject
import xenakis.model.registry.NamedObjectList

class NamedObjectListView<O : NamedObject>(
    val source: NamedObjectList<O>,
    val config: ObjectBoxConfig<O>,
    private val contentDisplay: ReactiveVariable<ContentDisplay>,
    private var filter: (O) -> Boolean = { true },
) : Control(), NamedObjectList.Listener<O> {
    constructor(
        source: NamedObjectList<O>, config: ObjectBoxConfig<O>,
        contentDisplay: ContentDisplay = config.supportedModes.first(),
        filter: (O) -> Boolean = { true },
    ) : this(source, config, reactiveVariable(contentDisplay), filter)

    private val storedWindowSize = mutableMapOf<ContentDisplay, Dimension2D>()

    var autoResizeScene = false

    private val boxes = mutableListOf<ObjectBox<O>>()
    private val boxesCache = mutableMapOf<O, ObjectBox<O>>()
    private fun getBox(obj: O) = boxesCache.getOrPut(obj) { ObjectBox(this, obj) }
    private var selectedBox: ObjectBox<O>? = null

    private val vbox = VBox()
    private val itemsScrollPane = ScrollPane(vbox).styleClass("items-scroll-bar")
    private var displayedContent: Parent? = null

    val actions = listActions.withContext(this)

    fun getBoxes(): List<Node> = boxes

    init {
        source.addListener(this, initialize = false)
        for (obj in source) {
            if (!filter(obj)) continue
            val box = getBox(obj)
            boxes.add(box)
        }
        vbox.children.addAll(boxes)
        setMode(contentDisplay.now)
    }

    val mode: ReactiveValue<ContentDisplay> get() = contentDisplay

    fun setMode(mode: ContentDisplay) {
        if (scene?.window != null) {
            storedWindowSize[contentDisplay.now] = Dimension2D(scene.window.width, scene.window.height)
        }
        contentDisplay.now = mode
        updateRoot(mode)
        if (autoResizeScene && scene?.window != null && mode in storedWindowSize) {
            val size = storedWindowSize.getValue(mode)
            scene.window.width = size.width
            scene.window.height = size.height
        } else autoResize()
    }

    private fun updateRoot(mode: ContentDisplay) {
        if (boxes.isEmpty()) {
            val objectType = plural(source.objectType)
            val emptyDisplay = VBox(Label("No $objectType to display")).centerChildren()
            emptyDisplay.setPadding(10.0)
            setRoot(emptyDisplay)
        } else {
            if (mode != ContentDisplay.DetailsPane) {
                setRoot(itemsScrollPane)
            }
            if (mode == ContentDisplay.SubWindow) {
                selectedBox?.showSubWindow()
            }
            for (box in boxes) box.setContentDisplay(mode)
            if (mode == ContentDisplay.DetailsPane) {
                displayContent(selectedBox)
            }
        }
    }

    private fun displayContent(box: ObjectBox<O>?) {
        val content = box?.content ?: Region()
        displayedContent = content as? ToolPane ?: ScrollPane(content)
        HBox.setHgrow(displayedContent, Priority.ALWAYS)
        setRoot(HBox(itemsScrollPane, displayedContent))
    }

    private fun autoResize() {
        if (mode.now != ContentDisplay.DetailsPane && autoResizeScene && scene?.window != null) {
            scene.window.sizeToScene()
            scene.window.height = scene.window.height.coerceAtMost(1000.0)
            scene.window.width = scene.window.width.coerceAtMost(1000.0)
        }
    }

    override fun added(obj: O, idx: Int) {
        if (!filter(obj)) return
        val j = getInsertionIndex(idx)
        val box = getBox(obj)
        boxes.add(j, box)
        vbox.children.add(j, box)
        updateRoot(mode.now)
        select(obj)
        autoResize()
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
        val box = boxesCache[obj] ?: return
        boxes.remove(box)
        vbox.children.remove(box)
        box.subWindow?.hide()
        if (displayedContent == box.content) {
            displayContent(null)
        }
        updateRoot(mode.now)
        autoResize()
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
        source.filter(filter).mapTo(boxes) { obj -> getBox(obj) }
        vbox.children.setAll(boxes)
        if (boxes.isNotEmpty()) select(0)
        autoResize()
    }

    fun select(box: ObjectBox<O>) {
        if (selectedBox == box) return
        selectedBox?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
        selectedBox = box
        box.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
        if (mode.now == ContentDisplay.DetailsPane) {
            displayContent(box)
        }
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
        val format = config.dataFormat(selected.obj) ?: return
        val content = mapOf(format to selected.obj)
        val clipboard = Clipboard.getSystemClipboard()
        clipboard.setContent(content)
    }

    private fun renameSelected() {
        val selected = selectedBox ?: return
        selected.nameControl?.startEdit()
    }

    fun showContent(obj: O) {
        select(obj)
        if (mode.now == ContentDisplay.SubWindow) {
            getBox(obj).showSubWindow()
        } else {
            val window = scene.window as SubWindow
            window.showOrBringToFront()
        }
    }

    enum class ContentDisplay {
        Inline, SubWindow, DetailsPane;

        companion object {
            val all = entries.toSet()
        }
    }

    companion object {
        val listActions = collectActions<NamedObjectListView<*>> {
            addAction("Rename selected") {
                shortcut("F2")
                executes { list -> list.renameSelected() }
            }
            addAction("Delete selected") {
                shortcut("Ctrl+DELETE")
                executes { list ->
                    val selected = list.selectedBox?.obj ?: return@executes
                    if (selected.canDelete) {
                        @Suppress("UNCHECKED_CAST")
                        val source = list.source as NamedObjectList<NamedObject>
                        source.remove(selected)
                    }
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

        private fun Action.Builder<NamedObjectListView<*>>.modeChange(mode: ContentDisplay) {
            applicableIf { view ->
                if (mode in view.config.supportedModes) view.mode.notEqualTo(mode)
                else reactiveValue(false)
            }
            executes { view -> view.setMode(mode) }
        }

        val modeChangeActions
            get() = collectActions {
                addAction("Display content inline") {
                    icon(MaterialDesignV.VIEW_SEQUENTIAL)
                    modeChange(ContentDisplay.Inline)
                }
                addAction("Display content in Sub-Window") {
                    icon(MaterialDesignD.DOCK_WINDOW)
                    modeChange(ContentDisplay.SubWindow)
                }
                addAction("Display content in side bar") {
                    icon(MaterialDesignV.VIEW_SPLIT_VERTICAL)
                    modeChange(ContentDisplay.DetailsPane)
                }
            }
    }
}