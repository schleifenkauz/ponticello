package xenakis.ui.registry

import fxutils.*
import fxutils.actions.Action
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.ScrollPane
import javafx.scene.input.Clipboard
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import org.kordamp.ikonli.materialdesign2.MaterialDesignV
import reaktive.value.*
import reaktive.value.binding.equalTo
import reaktive.value.binding.notEqualTo
import xenakis.model.obj.RenamableObject
import xenakis.model.registry.NamedObject
import xenakis.model.registry.NamedObjectList
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.controls.NameControl
import xenakis.ui.controls.NamePrompt
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage

class NamedObjectListView<O : NamedObject>(
    private val source: NamedObjectList<O>,
    val config: ObjectBoxConfig<O>,
    private val contentDisplay: ReactiveVariable<ContentDisplay>,
) : HBox(), NamedObjectList.Listener<O> {
    constructor(
        source: NamedObjectList<O>, config: ObjectBoxConfig<O>,
        contentDisplay: ContentDisplay = config.supportedModes.first(),
    ) : this(source, config, reactiveVariable(contentDisplay))

    var autoResizeScene = false

    private val boxes = mutableListOf<ObjectBox<O>>()
    private val boxesCache = mutableMapOf<O, ObjectBox<O>>()
    private fun getBox(obj: O) = boxesCache.getOrPut(obj) { ObjectBox(this, obj) }
    private var selectedBox: ObjectBox<O>? = null

    private val vbox = VBox()
    private val itemsScrollPane = ScrollPane(vbox).letContentFillViewPort().styleClass("items-scroll-bar")
    private var displayedContent: Parent? = null

    private var filter: (O) -> Boolean = { true }

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
        contentDisplay.now = mode
        children.setAll(itemsScrollPane)
        for (box in boxes) box.setContentDisplay(mode)
        if (mode == ContentDisplay.DetailsPane) {
            displayContent(selectedBox)
        }
        autoResize()
    }

    private fun displayContent(box: ObjectBox<O>?) {
        val content = box?.content ?: return
        displayedContent = content.let { content ->
            content as? ToolPane ?: ScrollPane(content)
        }
        children.add(content)
    }

    private fun autoResize() {
        if (autoResizeScene && scene?.window != null) {
            scene.window.sizeToScene()
        }
    }

    override fun added(obj: O, idx: Int) {
        if (!filter(obj)) return
        val j = getInsertionIndex(idx)
        val box = getBox(obj)
        boxes.add(j, box)
        vbox.children.add(j, box)
        select(obj)
        if (mode.now != ContentDisplay.DetailsPane) autoResize()
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
        val box = boxesCache.getValue(obj)
        boxes.remove(box)
        vbox.children.remove(box)
        box.subWindow?.hide()
        if (displayedContent == box.content) children.remove(displayedContent)
        if (mode.now != ContentDisplay.DetailsPane) autoResize()
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

    private fun select(box: ObjectBox<O>) {
        if (selectedBox == box) return
        selectedBox?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
        selectedBox = box
        box.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
        children.remove(displayedContent)
        displayContent(box)
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

    private class ObjectBox<O : NamedObject>(val parent: NamedObjectListView<O>, val obj: O) : VBox() {
        var subWindow: SubWindow? = null
            private set

        val config get() = parent.config

        val nameControl = if (obj is RenamableObject) NameControl(obj, config.getDefaultDisplayName(obj)) else null

        private val nameDisplay =
            nameControl ?: HBox(label(obj.name).styleClass("name-field")).styleClass("name")

        private val actionBar = ActionBar(
            config.getActions(obj) + objectActions.withContext(this),
            config.buttonStyle
        )

        private val space = infiniteSpace()

        private val header = HBox(nameDisplay, *config.getItemContent(obj).toTypedArray(), space, actionBar)

        val content = config.getContent(obj)

        init {
            space.setOnMousePressed { parent.select(this) }
            addEventFilter(MouseEvent.MOUSE_PRESSED) { parent.select(this) }
            styleClass("object-box")
            children.add(header.styleClass("object-box-header"))
            if (config.enableReordering) setupReordering()
            if (config.dataFormat(obj) != null) setupDragging()
        }

        fun setContentDisplay(option: ContentDisplay) {
            if (content == null) return
            if (option != ContentDisplay.SubWindow) {
                subWindow?.let { w ->
                    w.hide()
                    w.scene.root = Region()
                    subWindow = null
                }
            }
            if (option != ContentDisplay.Inline && content in children) {
                children.remove(content)
            }
            if (option == ContentDisplay.SubWindow) {
                subWindow = undecoratedSubWindow(content).also { w ->
                    config.configureSubWindow(w)
                    if (w.owner == null) w.initOwner(obj.context[primaryStage])
                }
            }
            if (option == ContentDisplay.Inline) {
                children.add(content)
            }
        }

        fun showSubWindow() {
            val w = this.subWindow ?: return
            w.showOrBringToFront()
        }

        private fun setupDragging() {
            val dragTarget = space
            dragTarget.setOnDragDetected { ev ->
                if (ev.isControlDown) {
                    val db = dragTarget.startDragAndDrop(TransferMode.COPY)
                    db.setContent(mapOf(config.dataFormat(obj) to obj.name.now))
                    ev.consume()
                }
            }
        }

        private fun setupReordering() {
            val dragTarget = actionBar.getButton(objectActions.getAction("Reorder"))
            dragTarget.setupDragging(
                onPressed = { viewOrder = 100.0 },
                relocateBy = { _, _, _, _, dy -> translateY = dy },
                onReleased = {
                    viewOrder = 0.0
                    var idx = parent.boxes.binarySearchBy(layoutY + translateY) { b -> b.layoutY }
                    if (idx < 0) idx = -(idx + 1)
                    val oldIndex = parent.boxes.indexOf(this)
                    try {
                        if (idx != oldIndex) {
                            parent.source.move(obj, idx)
                        }
                    } finally {
                        translateY = 0.0
                    }
                }
            )
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

        private val objectActions = collectActions<ObjectBox<*>> {
            addAction("Edit object details") {
                icon { box ->
                    val config = box.config as ObjectBoxConfig<NamedObject>
                    reactiveValue(config.detailWindowIcon(box.obj))
                }
                shortcuts("Ctrl+E")
                applicableIf { box -> box.parent.contentDisplay.equalTo(ContentDisplay.SubWindow) }
                executes { box, ev ->
                    box.showSubWindow()
                }
            }
            @Suppress("UNCHECKED_CAST")
            addAction("Duplicate object") {
                applicableIf { box -> reactiveValue(box.obj.canCopy && box.obj.registry != null) }
                description { box -> reactiveValue(box.parent.source.objectType) }
                executes { box, ev ->
                    val obj = box.obj
                    val list = box.parent.source as ObjectRegistry<NamedObject>
                    val initialName = obj.name.now + "_copy"
                    val name = NamePrompt(list, "Name for new duplicate instrument", initialName)
                        .showDialog(ev) ?: return@executes
                    val copy = obj.copy(name)
                    list.add(copy, list.indexOf(obj))
                }
            }
            addAction("Reorder") {
                icon(MaterialDesignR.REORDER_HORIZONTAL)
                applicableIf { box -> reactiveValue(box.config.enableReordering) }
            }
            addAction("Delete object") {
                icon(Material2AL.DELETE)
                shortcuts("Ctrl+DELETE")
                applicableIf { box -> reactiveValue(box.obj.canDelete) }
                executes { box ->
                    val source = box.parent.source as NamedObjectList<NamedObject>
                    source.remove(box.obj)
                }
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
            get() = collectActions<NamedObjectListView<*>> {
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