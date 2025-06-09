package ponticello.ui.registry

import fxutils.*
import fxutils.actions.Action
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.model.obj.ContextualObject
import ponticello.model.obj.RenamableObject
import ponticello.model.obj.withName
import ponticello.model.registry.NamedObject
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.controls.NameControl
import ponticello.ui.controls.NamePrompt
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.binding.equalTo
import reaktive.value.now
import reaktive.value.reactiveValue

class ObjectBox<O : ContextualObject>(val parent: ObjectListView<O>, val obj: O) : VBox() {
    var subWindow: SubWindow? = null
        private set

    val config get() = parent.config

    val nameControl = if (obj is RenamableObject) NameControl(obj, config.getDefaultDisplayName(obj)) else null

    private val nameDisplay =
        when {
            nameControl != null -> nameControl.setFixedWidth(150.0)
            obj is NamedObject -> HBox(label(obj.name).styleClass("name-field")).styleClass("name")
            else -> null
        }

    val actionBar = ActionBar(
        config.getActions(this) + objectActions.withContext(this),
        config.buttonStyle
    )

    private val header = HBox() styleClass "object-box-header"

    private lateinit var currentMode: DisplayMode

    var content: Parent? = null

    fun content(): Parent? {
        content?.let { return it }
        content = config.getContent(obj, currentMode) ?: return null
        return content!!
    }

    init {
        styleClass("object-box")
        if (nameDisplay != null) header.children.add(nameDisplay)
        header.children.addAll(config.getItemContent(obj))
        if (config.addSpaceBeforeActionBar) header.children.add(infiniteSpace())
        header.children.add(actionBar)
        if (config.enableReordering) setupReordering()
        if (obj is NamedObject && config.dataFormat(obj) != null) setupDragging()
        children.setAll(header)
        addEventFilter(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.target is Button || ev.target is SliderBar<*>) return@addEventFilter
            parent.select(this)
            if (ev.clickCount == 2) {
                parent.showSelected()
            }
        }
    }

    fun setContentDisplay(mode: DisplayMode) {
        content = null
        currentMode = mode
        if (mode != DisplayMode.SubWindow) {
            subWindow?.let { w ->
                w.hide()
                w.scene.root = Region()
                subWindow = null
            }
        }
        if (mode != DisplayMode.Inline) {
            children.setAll(header)
        }
        if (mode == DisplayMode.Inline) {
            content = config.getContent(obj, mode) ?: return
            children.setAll(header, content)
        }
    }

    private fun createSubWindow(): SubWindow? {
        content = config.getContent(obj, DisplayMode.SubWindow) ?: return null
        val objectType = parent.source.objectType
        val name = if (obj is NamedObject) obj.name.now else ""
        val title = "$objectType $name"
        val window =
            if (obj is NamedObject) makeSubWindow(obj, content!!)
            else makeSubWindow(content!!, title, obj.context).also { it.sizeToScene() }
        config.configureSubWindow(window, obj)
        window.initOwner(obj.context[primaryStage])
        return window
    }

    fun showSubWindow(): SubWindow? {
        if (subWindow == null) {
            subWindow = createSubWindow()
        }
        val w = subWindow ?: return null
        w.showOrBringToFront()
        return w
    }

    private fun setupDragging() {
        val dragTarget = actionBar.getButton(objectActions.getAction("Drag"))
        obj as NamedObject
        dragTarget.setOnDragDetected { ev ->
            val transferMode = if (ev.isControlDown && obj.canCopy) TransferMode.COPY else TransferMode.MOVE
            val db = dragTarget.startDragAndDrop(transferMode)
            db.setContent(mapOf(config.dataFormat(obj) to obj.name.now))
            config.configureDragboard(obj, db)
            ev.consume()
        }
    }

    private fun setupReordering() {
        val dragTarget = actionBar.getButton(objectActions.getAction("Reorder"))
        dragTarget.setupDragging(
            onPressed = { viewOrder = 100.0 },
            relocateBy = { _, _, _, _, dy -> translateY = dy },
            onReleased = {
                viewOrder = 0.0
                var idx = parent.getBoxes().binarySearchBy(layoutY + translateY) { b -> b.layoutY }
                if (idx < 0) idx = -(idx + 1)
                val oldIndex = parent.getBoxes().indexOf(this)
                try {
                    if (idx != oldIndex) {
                        if (idx > oldIndex) idx--
                        parent.source.move(obj, idx)
                    }
                } finally {
                    translateY = 0.0
                }
            }
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private val objectActions = collectActions<ObjectBox<*>> {
            addAction("Edit object details") {
                icon { box ->
                    val config = box.config as ObjectListDisplayConfig<ContextualObject>
                    reactiveValue(config.detailWindowIcon(box.obj))
                }
                shortcuts("Ctrl+E")
                enableWhen { box -> box.parent.mode.equalTo(DisplayMode.SubWindow) }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                executes { box, _ ->
                    box.showSubWindow()
                }
            }
            addAction("Duplicate object") {
                applicableIf { box -> box.obj is RenamableObject && box.obj.canCopy && box.obj.registry != null }
                icon(MaterialDesignC.CONTENT_DUPLICATE)
                description { box -> reactiveValue("Duplicate ${box.parent.source.objectType}") }
                executes { box, ev ->
                    val obj = box.obj as RenamableObject
                    val list = box.parent.source as ObjectRegistry<RenamableObject>
                    val initialName = obj.name.now + "_copy"
                    val name = NamePrompt(list, "Name for new duplicate instrument", initialName)
                        .showDialog(ev) ?: return@executes
                    val copy = obj.copy().withName(name)
                    list.add(copy, list.indexOf(obj) + 1)
                }
            }
            addAction("Drag") {
                icon(MaterialDesignC.CURSOR_POINTER)
                applicableIf { box ->
                    val config = box.config as ObjectListDisplayConfig<NamedObject>
                    box.obj is NamedObject && config.dataFormat(box.obj) != null }
            }
            addAction("Reorder") {
                icon(MaterialDesignR.REORDER_HORIZONTAL)
                applicableIf { box -> box.config.enableReordering }
            }
            addAction("Delete object") {
                icon(Material2AL.DELETE)
                shortcuts("Ctrl+DELETE")
                applicableIf { box -> box.obj !is NamedObject || box.obj.canDelete }
                executes { box ->
                    val source = box.parent.source as ObjectList<ContextualObject>
                    source.remove(box.obj)
                }
            }
        }
    }
}
