package ponticello.ui.registry

import fxutils.*
import fxutils.actions.Action
import fxutils.actions.collectActions
import fxutils.actions.registerActions
import fxutils.prompt.YesNoPrompt
import javafx.application.Platform
import javafx.event.Event
import javafx.geometry.Dimension2D
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.input.*
import javafx.scene.layout.*
import javafx.stage.Window
import kotlinx.serialization.Serializable
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignV
import ponticello.model.obj.RenamableObject
import ponticello.model.obj.withName
import ponticello.model.registry.NamedObject
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.controls.NamePrompt
import ponticello.ui.dock.ToolPane
import reaktive.value.*
import reaktive.value.binding.map
import reaktive.value.binding.notEqualTo

class ObjectListView<O : Any>(
    val source: ObjectList<O>,
    val config: ListDisplayConfig<O>,
    private val scrollable: Boolean = true,
    private val displayMode: ReactiveVariable<DisplayMode>,
) : Control(), ObjectList.Listener<O> {
    constructor(
        source: ObjectList<O>, config: ListDisplayConfig<O>,
        scrollable: Boolean = true,
        displayMode: DisplayMode = config.supportedModes.first(),
    ) : this(source, config, scrollable, reactiveVariable(displayMode))

    private val dropPreviewNode = Region() styleClass "drop-preview"

    private val storedWindowSizes = mutableMapOf<DisplayMode, Dimension2D>()

    var autoResizeScene = false

    private val boxes = mutableListOf<ObjectBox<O>>()
    private val boxesCache = mutableMapOf<O, ObjectBox<O>>()
    private val selectedBox: ReactiveVariable<ObjectBox<O>?> = reactiveVariable(null)

    private var itemsLayout: Pane = Pane()
        set(value) {
            field = value
            value.children.addAll(boxes)
            setupDropArea()
            if (scrollable) {
                itemsScrollPane.content = value
            }
        }

    val itemsScrollPane = ScrollPane(Pane()).styleClass("items-scroll-bar")

    private var displayedContent: Parent? = null

    val actions = listActions.withContext(this)

    val mode: ReactiveValue<DisplayMode> get() = displayMode

    val orientation get() = if (mode.now is DisplayMode.Inline) config.inlineOrientation else Orientation.VERTICAL

    fun getBox(obj: O) = boxesCache.getOrPut(obj) { ObjectBox(this, obj) }

    fun getBoxes(): List<ObjectBox<*>> = boxes

    init {
        styleClass(*config.listStyle)
        source.addListener(this, initialize = false)
        for (obj in source) {
            if (!config.filter(obj)) continue
            val box = getBox(obj)
            boxes.add(box)
        }
        itemsScrollPane.isFitToWidth = true
        itemsScrollPane.isFitToHeight = true
        setMode(displayMode.now)
        registerSelectionShortcuts()
    }

    private fun setupDropArea() {
        itemsLayout.addEventHandler(DragEvent.ANY) { ev ->
            when (ev.eventType) {
                DragEvent.DRAG_ENTERED -> {
                    val dragged = ev.gestureSource
                    when {
                        dragged is ObjectBox<*> -> dropPreviewNode.setPrefSize(dragged.width, dragged.height)
                        orientation == Orientation.HORIZONTAL -> dropPreviewNode.setPrefSize(20.0, this.height)
                        orientation == Orientation.VERTICAL -> dropPreviewNode.setPrefSize(this.width, 20.0)
                    }
                }

                DragEvent.DRAG_OVER -> {
                    val acceptedTransferModes = config.acceptedTransferModes(ev.dragboard)
                    if (acceptedTransferModes.isNotEmpty()) {
                        ev.acceptTransferModes(*acceptedTransferModes)
                        ev.consume()
                        val idx = getBoxIndexFromY(ev.screenX, ev.screenY, boxes)
                        val prevPosition = itemsLayout.children.indexOf(dropPreviewNode)
                        if (idx != prevPosition) {
                            if (prevPosition != -1) {
                                itemsLayout.children.removeAt(prevPosition)
                            }
                            itemsLayout.children.add(idx, dropPreviewNode)
                        }
                    }
                }

                DragEvent.DRAG_EXITED -> {
                    ev.consume()
                    itemsLayout.children.remove(dropPreviewNode)
                }

                DragEvent.DRAG_DROPPED -> {
                    ev.consume()
                    val obj = config.getDroppedObject(ev) ?: getObjectFromSource(ev)
                    if (obj != null) {
                        val idx = getBoxIndexFromY(ev.screenX, ev.screenY, boxes.filter { b -> b.obj != obj })
                        if (obj in source) {
                            source.move(obj, idx)
                            if (config.hideWhileDragging) {
                                val box = getBox(obj)
                                box.isVisible = true
                                box.isManaged = true
                            }
                        } else {
                            val gestureSource = ev.gestureSource
                            if (ev.transferMode == TransferMode.MOVE && gestureSource is ObjectBox<*>) {
                                @Suppress("UNCHECKED_CAST")
                                val source = gestureSource.parent.source as ObjectList<O>
                                source.remove(obj)
                            }
                            config.dropObject(obj, idx, source)
                        }
                    }
                    ev.isDropCompleted = true
                }
            }
        }
    }

    private fun getObjectFromSource(ev: DragEvent): O? {
        if (ev.gestureSource !in boxes) return null
        val dragboard = ev.dragboard
        if (!dragboard.hasContent(config.dataFormat)) return null
        if (source !is NamedObjectList) return null
        val name = dragboard.getContent(config.dataFormat) as? String ?: return null
        return source.get(name)
    }

    private fun getBoxIndexFromY(screenX: Double, screenY: Double, boxes: List<ObjectBox<O>>): Int {
        val idx = when (orientation) {
            Orientation.VERTICAL -> boxes
                .map { it.localToScreen(it.boundsInLocal).middleY }
                .binarySearch(screenY)
            Orientation.HORIZONTAL -> boxes
                .map { it.localToScreen(it.boundsInLocal).middleX }
                .binarySearch(screenX)
        }
        return if (idx < 0) -idx - 1 else idx
    }

    fun startDrag(obj: O) {
        if (config.hideWhileDragging) {
            val box = getBox(obj)
            box.isVisible = false
            box.isManaged = false
        }
    }

    private fun registerSelectionShortcuts() {
        registerShortcuts {
            if (!config.enableSelection) return@registerShortcuts
            val selected = selectedBox.now ?: return@registerShortcuts
            registerActions(selected.actionBar.actions())
            val selectedContent = selected.content()
            if (displayMode.now == DisplayMode.DetailsPane && selectedContent is ToolPane) {
                val actionBar = selectedContent.actionBar
                registerActions(actionBar.actions())
            }
        }
    }

    fun setMode(mode: DisplayMode) {
        val horizontalLayout = mode is DisplayMode.Inline && config.inlineOrientation == Orientation.HORIZONTAL
        if (horizontalLayout && itemsLayout !is HBox) {
            itemsLayout = HBox()
        }
        if (!horizontalLayout && itemsLayout !is VBox) {
            itemsLayout = VBox()
        }
        if (scene?.window != null) {
            storedWindowSizes[displayMode.now] = Dimension2D(scene.window.width, scene.window.height)
        }
        displayMode.now = mode
        for (box in boxes) {
            box.updateMode()
        }
        updateRoot(mode)
        if (autoResizeScene && scene?.window != null && mode in storedWindowSizes) {
            val size = storedWindowSizes.getValue(mode)
            scene.window.width = size.width
            scene.window.height = size.height
        } else autoResize()
    }

    private fun addObjectButton(): Node {
        val button = button("Add ${source.objectType}") { ev ->
            addObject(ev)
        }
        button.tooltip = Tooltip("Type Ctrl+PLUS to add a new ${source.objectType}.")
        return if (config.centerAddObjectButton) VBox(button).centerChildren() else button
    }

    private fun addObject(ev: Event? = null) {
        val newObj = config.createNewObject(ev, source) ?: return
        if (source is NamedObjectList && newObj is NamedObject && source.has(newObj.name.now)) {
            val prompt = YesNoPrompt("Overwrite ${source.objectType} ${newObj.name.now}?")
            if (prompt.showDialog(ev) != true) return
        }
        source.add(newObj)
        if (config.enableSelection) {
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
            if (config.enableAddObjectButton) setRoot(addObjectButton())
            else setRoot(emptyDisplay())
        } else {
            if (mode == DisplayMode.DetailsPane) {
                displayContent(selectedBox.now)
            } else {
                setRoot(itemCellsLayout())
            }
        }
    }

    private fun itemCellsLayout(): Region {
        val itemsPane = if (scrollable) itemsScrollPane else itemsLayout
        return if (config.enableAddObjectButton) VBox(itemsPane, addObjectButton())
        else itemsPane
    }

    private fun displayContent(box: ObjectBox<O>?) {
        val content = box?.content()
        if (content != null) {
            displayedContent = content
            HBox.setHgrow(content, Priority.ALWAYS)
            setRoot(HBox(itemCellsLayout(), content))
        } else {
            setRoot(itemCellsLayout())
        }
    }

    private fun autoResize() {
//        if (mode.now != DisplayMode.DetailsPane && autoResizeScene && scene?.window != null) {
//            scene.window.sizeToScene()
//            scene.window.height = scene.window.height.coerceAtMost(1000.0)
//            scene.window.width = scene.window.width.coerceAtMost(1000.0)
//        }
    }

    override fun added(obj: O, idx: Int) = Platform.runLater {
        if (!config.filter(obj)) return@runLater
        val j = getInsertionIndex(idx)
        val box = getBox(obj)
        box.updateMode()
        boxes.add(j, box)
        itemsLayout.children.add(j, box)
        updateRoot(mode.now)
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

    override fun removed(obj: O) = Platform.runLater {
        val box = boxesCache[obj] ?: return@runLater
        val idx = boxes.indexOf(box)
        boxes.removeAt(idx)
        itemsLayout.children.remove(box)
        box.subWindow?.hide()
        if (selectedBox.now == box) {
            val nextToSelect = boxes.getOrNull(idx) ?: boxes.getOrNull(idx - 1)
            if (nextToSelect != null) {
                select(nextToSelect)
            } else {
                deselectAll()
            }
        }
        updateRoot(mode.now)
        autoResize()
    }

    override fun moved(obj: O, idx: Int) {
        removed(obj)
        added(obj, idx)
    }

    fun refilter() {
        //TODO we could use isManaged/isVisible here.
        boxes.clear()
        source.filter(config::filter).mapTo(boxes) { obj -> getBox(obj) }
        itemsLayout.children.setAll(boxes)
        if (boxes.isNotEmpty() && config.enableSelection) select(0)
        autoResize()
    }

    fun deselectAll() {
        val selected = selectedBox.now
        if (selected != null) {
            selected.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
            selectedBox.now = null
            config.onDeselected(selected.obj)
        }

        setRoot(itemCellsLayout())
    }

    fun select(box: ObjectBox<O>) {
        if (!config.enableSelection) return
        val prevSelected = selectedBox.now
        if (prevSelected == box) return
        selectedBox.now = box
        if (prevSelected != null) {
            prevSelected.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
            config.onDeselected(prevSelected.obj)
        }

        box.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
        if (mode.now == DisplayMode.DetailsPane) {
            displayContent(box)
        }
        config.onSelected(box.obj)
        runFXWithTimeout(10) {
            if (!box.isFocusWithin) {
                box.requestFocus()
            }
        }
    }

    fun select(idx: Int) {
        if (!config.enableSelection) return
        if (idx !in boxes.indices) return
        select(boxes[idx])
    }

    fun select(obj: O) {
        if (!config.enableSelection) return
        val box = boxes.find { b -> b.obj == obj } ?: error("No box for $obj")
        select(box)
    }

    fun selectedBox(): ReactiveValue<ObjectBox<O>?> = selectedBox

    fun selectedObject(): O? = selectedBox.now?.obj

    fun selectedIndex(): Int = boxes.indexOf(selectedBox.now)

    private fun navigate(deltaIdx: Int) {
        if (!config.enableSelection) return
        if (selectedBox.now != null) {
            val idx = boxes.indexOf(selectedBox.now!!)
            val newIdx = (idx + deltaIdx).coerceIn(boxes.indices)
            select(boxes[newIdx])
        } else if (boxes.isNotEmpty()) {
            if (deltaIdx < 0) select(boxes.last())
            else select(boxes.first())
        }
    }

    private fun moveSelected(deltaIdx: Int) {
        if (!config.enableSelection) return
        val selected = selectedBox.now ?: return
        val idx = boxes.indexOf(selected)
        val newIdx = idx + deltaIdx
        if (newIdx !in boxes.indices) return
        source.move(selected.obj, newIdx)
    }

    private fun copySelected() {
        if (!config.enableSelection) return
        val selected = selectedBox.now ?: return
        val format = config.dataFormat ?: return
        val content = mapOf(format to selected.obj)
        val clipboard = Clipboard.getSystemClipboard()
        clipboard.setContent(content)
    }

    private fun renameSelected() {
        if (!config.enableSelection) return
        val selected = selectedBox.now ?: return
        selected.nameControl?.startEdit()
    }

    fun showContent(obj: O): Window? {
        if (mode.now == DisplayMode.SubWindow) {
            return getBox(obj).showSubWindow()
        } else {
            select(obj)
            Platform.runLater {
                getBox(obj).content()?.requestFocus()
            }
            return null
        }
    }

    fun showSelected() {
        if (!config.enableSelection) return
        val selected = selectedBox.now?.obj ?: return
        showContent(selected)
    }

    fun initializeContent(obj: O) {
        val box = boxesCache[obj] ?: return
        box.content()
    }

    @Serializable
    sealed class DisplayMode {
        companion object {
            val all get() = setOf(Inline(false), Inline(true), SubWindow, DetailsPane)

            val Collapsable get() = Inline(collapsable = true)
        }

        @Serializable
        data class Inline(val collapsable: Boolean) : DisplayMode()

        @Serializable
        data object SubWindow : DisplayMode()

        @Serializable
        data object DetailsPane : DisplayMode()
    }

    companion object {
        val listActions = collectActions<ObjectListView<*>> {
            addAction("Delete selected") {
                shortcut("Ctrl+DELETE")
                icon(Material2MZ.REMOVE)
                enableWhen { list ->
                    list.selectedBox.map { box ->
                        @Suppress("UNCHECKED_CAST")
                        val config = list.config as ListDisplayConfig<Any>
                        box != null && config.canDelete(box.obj)
                    }
                }
                executes { list ->
                    val selected = list.selectedBox.now?.obj ?: return@executes

                    @Suppress("UNCHECKED_CAST")
                    val source = list.source as ObjectList<Any>
                    source.remove(selected)
                }
            }
//            addAction("Deselect all") {
//                shortcut("ESCAPE")
//                executes { list -> list.deselectAll() }
//            }
            addAction("Rename selected") {
                shortcut("F2")
                executes { list -> list.renameSelected() }
            }
            addAction("Create object") {
                description { list -> reactiveValue("Create new ${list.source.objectType}") }
                shortcut("Ctrl+PLUS")
                icon(MaterialDesignP.PLUS)
                executes { p, ev -> p.addObject(ev) }
            }
            addAction("Select previous") {
                shortcuts("UP", "LEFT")
                executes { list, ev ->
                    if (ev !is KeyEvent) return@executes
                    if (ev.code == KeyCode.UP && list.orientation == Orientation.VERTICAL) {
                        list.navigate(-1)
                    }
                    if (ev.code == KeyCode.LEFT && list.orientation == Orientation.HORIZONTAL) {
                        list.navigate(-1)
                    }
                }
            }
            addAction("Select next") {
                shortcuts("DOWN", "RIGHT")
                executes { list, ev ->
                    if (ev !is KeyEvent) return@executes
                    if (ev.code == KeyCode.DOWN && list.orientation == Orientation.VERTICAL) {
                        list.navigate(+1)
                    }
                    if (ev.code == KeyCode.RIGHT && list.orientation == Orientation.HORIZONTAL) {
                        list.navigate(+1)
                    }
                }
            }
            addAction("Swap with previous") {
                shortcuts("Ctrl+UP", "Ctrl+LEFT")
                executes { list, ev ->
                    if (ev !is KeyEvent) return@executes
                    if (ev.code == KeyCode.UP && list.orientation == Orientation.VERTICAL) {
                        list.moveSelected(-1)
                    }
                    if (ev.code == KeyCode.LEFT && list.orientation == Orientation.HORIZONTAL) {
                        list.moveSelected(-1)
                    }
                }
            }
            addAction("Swap with next") {
                shortcuts("Ctrl+DOWN", "Ctrl+RIGHT")
                executes { list, ev ->
                    if (ev !is KeyEvent) return@executes
                    if (ev.code == KeyCode.DOWN && list.orientation == Orientation.VERTICAL) {
                        list.moveSelected(+1)
                    }
                    if (ev.code == KeyCode.RIGHT && list.orientation == Orientation.HORIZONTAL) {
                        list.moveSelected(+1)
                    }
                }
            }
            addAction("Copy item") {
                shortcut("Ctrl+C")
                executes { list -> list.copySelected() }
            }
            addAction("Duplicate object") {
                enableWhen { list ->
                    list.selectedBox.map { box ->
                        box != null && box.obj is RenamableObject && box.obj.canCopy && box.obj.registry != null
                    }
                }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                icon(MaterialDesignC.CONTENT_DUPLICATE)
                description { list -> reactiveValue("Duplicate ${list.source.objectType}") }
                executes { list, ev ->
                    val obj = list.selectedObject() as? RenamableObject ?: return@executes

                    @Suppress("UNCHECKED_CAST")
                    val source = list.source as ObjectRegistry<RenamableObject>
                    val initialName = obj.name.now + "_copy"
                    val name = NamePrompt(source, "Name for new duplicate instrument", initialName)
                        .showDialog(ev) ?: return@executes
                    val copy = obj.copy().withName(name)
                    source.add(copy, source.indexOf(obj) + 1)
                }
            }
            addAction("Focus selected object") {
                shortcut("Enter")
                executes { list -> list.showSelected() }
            }
        }

        private fun Action.Builder<ObjectListView<*>>.modeChange(mode: DisplayMode) {
            enableWhen { view ->
                if (mode in view.config.supportedModes) view.mode.notEqualTo(mode)
                else reactiveValue(false)
            }
            ifNotApplicable(Action.IfNotApplicable.Hide)
            executes { view -> view.setMode(mode) }
        }

        val modeChangeActions
            get() = collectActions {
                for (collapsable in listOf(false, true)) {
                    addAction("Display content inline") {
                        icon { view: ObjectListView<*> ->
                            when (view.config.inlineOrientation) {
                                Orientation.HORIZONTAL -> reactiveValue(MaterialDesignV.VIEW_WEEK)
                                Orientation.VERTICAL -> reactiveValue(MaterialDesignV.VIEW_SEQUENTIAL)
                            }
                        }
                        modeChange(DisplayMode.Inline(collapsable))
                    }
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