package xenakis.ui.registry

import fxutils.*
import fxutils.actions.Action
import fxutils.actions.collectActions
import fxutils.actions.registerActions
import javafx.application.Platform
import javafx.geometry.Dimension2D
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.input.Clipboard
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import kotlinx.serialization.Serializable
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import org.kordamp.ikonli.materialdesign2.MaterialDesignV
import reaktive.value.*
import reaktive.value.binding.notEqualTo
import xenakis.model.registry.NamedObject
import xenakis.model.registry.NamedObjectList

class NamedObjectListView<O : NamedObject>(
    val source: NamedObjectList<O>,
    val config: ObjectBoxConfig<O>,
    private val displayMode: ReactiveVariable<DisplayMode>,
    private var filter: (O) -> Boolean = { true },
) : Control(), NamedObjectList.Listener<O> {
    constructor(
        source: NamedObjectList<O>, config: ObjectBoxConfig<O>,
        displayMode: DisplayMode = config.supportedModes.first(),
        filter: (O) -> Boolean = { true },
    ) : this(source, config, reactiveVariable(displayMode), filter)

    private val storedWindowSizes = mutableMapOf<DisplayMode, Dimension2D>()

    var autoResizeScene = false

    private val boxes = mutableListOf<ObjectBox<O>>()
    private val boxesCache = mutableMapOf<O, ObjectBox<O>>()
    private fun getBox(obj: O) = boxesCache.getOrPut(obj) { ObjectBox(this, obj) }
    private var selectedBox: ObjectBox<O>? = null

    private val vbox = VBox()
    private val itemsScrollPane = ScrollPane(vbox).styleClass("items-scroll-bar")
    private var displayedContent: Parent? = null

    val actions = listActions.withContext(this)

    fun getBoxes(): List<ObjectBox<*>> = boxes

    init {
        source.addListener(this, initialize = false)
        for (obj in source) {
            if (!filter(obj)) continue
            val box = getBox(obj)
            boxes.add(box)
        }
        vbox.children.addAll(boxes)
        setMode(displayMode.now)
        registerShortcuts {
            val selected = selectedBox ?: return@registerShortcuts
            registerActions(selected.actionBar.actions())
            val selectedContent = selected.content
            if (displayMode.now != DisplayMode.DetailsPane && selectedContent is ToolPane) {
                registerActions(selectedContent.actionBar.actions())
            }
        }
    }

    val mode: ReactiveValue<DisplayMode> get() = displayMode

    fun setMode(mode: DisplayMode) {
        if (scene?.window != null) {
            storedWindowSizes[displayMode.now] = Dimension2D(scene.window.width, scene.window.height)
        }
        displayMode.now = mode
        updateRoot(mode)
        if (autoResizeScene && scene?.window != null && mode in storedWindowSizes) {
            val size = storedWindowSizes.getValue(mode)
            scene.window.width = size.width
            scene.window.height = size.height
        } else autoResize()
    }

    private fun addObjectButton(): Node {
        val button = button("Add ${source.objectType}") {
            addObject()
        }
        button.tooltip = Tooltip("Type Ctrl+PLUS to add a new ${source.objectType}.")
        return if (config.centerAddObjectButton) VBox(button).centerChildren() else button
    }

    private fun addObject() {
        val newObj = config.createNewObject() ?: return
        source.add(newObj)
        if (filter(newObj)) {
            select(newObj)
        }
    }

    private fun emptyDisplay(): VBox {
        val objectType = plural(source.objectType)
        val emptyDisplay = VBox(Label("No $objectType to display"))
        emptyDisplay.centerChildren()
        emptyDisplay.setPadding(10.0)
        return emptyDisplay
    }

    private fun updateRoot(mode: DisplayMode) {
        if (boxes.isEmpty()) {
            val root =
                if (config.enableAddObjectButton) addObjectButton()
                else emptyDisplay()
            setRoot(root)
        } else {
            if (mode != DisplayMode.DetailsPane) {
                setRoot(itemCellsLayout())
            }
            if (mode == DisplayMode.SubWindow) {
                selectedBox?.showSubWindow()
            }
            for (box in boxes) box.setContentDisplay(mode)
            if (mode == DisplayMode.DetailsPane) {
                displayContent(selectedBox)
            }
        }
    }

    private fun itemCellsLayout() =
        if (config.enableAddObjectButton) VBox(itemsScrollPane, addObjectButton())
        else itemsScrollPane

    private fun displayContent(box: ObjectBox<O>?) {
        val content = box?.content ?: Region()
        displayedContent = content as? ToolPane ?: ScrollPane(content)
        HBox.setHgrow(displayedContent, Priority.ALWAYS)
        setRoot(HBox(itemCellsLayout(), displayedContent))
    }

    private fun autoResize() {
        if (mode.now != DisplayMode.DetailsPane && autoResizeScene && scene?.window != null) {
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
        if (mode.now == DisplayMode.DetailsPane) {
            displayContent(box)
        }
        config.onSelected(box.obj)
    }

    fun select(idx: Int) {
        if (idx !in boxes.indices) return
        select(boxes[idx])
    }

    fun select(obj: O) {
        val box = boxes.find { b -> b.obj == obj } ?: error("No box for $obj")
        select(box)
    }

    fun selectedIndex(): Int = boxes.indexOf(selectedBox)

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
        if (mode.now == DisplayMode.SubWindow) {
            getBox(obj).showSubWindow()
        } else {
            select(obj)
            val window = scene.window as SubWindow
            window.showOrBringToFront()
            Platform.runLater {
                getBox(obj).content?.requestFocus()
            }
        }
    }

    fun showSelected() {
        val selected = selectedBox?.obj ?: return
        showContent(selected)
    }

    @Serializable
    enum class DisplayMode {
        Inline, SubWindow, DetailsPane;

        companion object {
            val all = entries.toSet()
        }
    }

    companion object {
        val listActions = collectActions<NamedObjectListView<*>> {
            addAction("Add object") {
                shortcut("Ctrl+PLUS")
                executes { list -> list.addObject() }
            }
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
                applicableIf { list -> list.config.enableReordering }
                executes { list -> list.moveSelected(-1) }
            }
            addAction("Move down") {
                shortcut("Ctrl+DOWN")
                applicableIf { list -> list.config.enableReordering }
                executes { list -> list.moveSelected(+1) }
            }
            addAction("Copy item") {
                shortcut("Ctrl+C")
                executes { list -> list.copySelected() }
            }
            addAction("Focus selected object") {
                shortcut("Enter")
                executes { list -> list.showSelected() }
            }
        }

        private fun Action.Builder<NamedObjectListView<*>>.modeChange(mode: DisplayMode) {
            applicableWhen { view ->
                if (mode in view.config.supportedModes) view.mode.notEqualTo(mode)
                else reactiveValue(false)
            }
            executes { view -> view.setMode(mode) }
        }

        val modeChangeActions
            get() = collectActions {
                addAction("Display content inline") {
                    icon(MaterialDesignV.VIEW_SEQUENTIAL)
                    modeChange(DisplayMode.Inline)
                }
                addAction("Display content in Sub-Window") {
                    icon(MaterialDesignD.DOCK_WINDOW)
                    modeChange(DisplayMode.SubWindow)
                }
                addAction("Display content in side bar") {
                    icon(MaterialDesignV.VIEW_SPLIT_VERTICAL)
                    modeChange(DisplayMode.DetailsPane)
                }
            }
    }
}