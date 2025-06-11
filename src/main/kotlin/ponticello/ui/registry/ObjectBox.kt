package ponticello.ui.registry

import fxutils.*
import fxutils.actions.Action
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.model.obj.ContextualObject
import ponticello.model.obj.RenamableObject
import ponticello.model.obj.withName
import ponticello.model.registry.NamedObject
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.controls.NameControl
import ponticello.ui.controls.NamePrompt
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.binding.equalTo
import reaktive.value.now
import reaktive.value.reactiveValue

class ObjectBox<O : Any>(val parent: ObjectListView<O>, val obj: O, private var currentMode: DisplayMode) : Control() {
    var subWindow: SubWindow? = null
        private set

    private var prevDragTarget: Node? = null

    val config get() = parent.config

    val nameControl = if (obj is RenamableObject) NameControl(obj, config.getDefaultDisplayName(obj)) else null

    val nameLabel: Label?

    private val nameDisplay: HBox? = when {
        nameControl != null -> {
            nameLabel = nameControl.label
            nameControl.setFixedWidth(150.0)
        }

        obj is NamedObject -> {
            nameLabel = label(obj.name.now).styleClass("name-field")
            HBox(nameLabel).styleClass("name")
        }

        else -> {
            nameLabel = null
            null
        }
    }

    val actionBar = ActionBar(
        config.getActions(this) + objectActions.withContext(this),
        config.buttonStyle
    )

    val header = HBox() styleClass "object-box-header"

    var content: Parent? = null

    fun content(): Parent? {
        content?.let { return it }
        content = config.getContent(obj, currentMode) ?: return null
        return content!!
    }

    init {
        if (nameDisplay != null) header.children.add(nameDisplay)
        header.children.addAll(config.getItemContent(obj))
        if (config.addSpaceBeforeActionBar) header.children.add(infiniteSpace())
        header.children.add(actionBar)

        addEventFilter(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.target is Button || ev.target is SliderBar<*>) return@addEventFilter
            parent.select(this)
            if (ev.clickCount == 2) {
                parent.showSelected()
            }
        }
        styleClass(*config.boxStyle)
        relayout()
    }

    fun setContentDisplay(mode: DisplayMode) {
        if (mode == currentMode) return
        currentMode = mode
        relayout()
    }

    private fun relayout() {
        content = null
        if (currentMode != DisplayMode.SubWindow) {
            subWindow?.let { w ->
                w.hide()
                w.scene.root = Region()
                subWindow = null
            }
        }
        if (currentMode == DisplayMode.Inline) {
            content = config.getContent(obj, currentMode)
        }
        val root = config.boxLayout(obj, header, content)
        setPseudoClassState("inline-content", currentMode == DisplayMode.Inline && content != null)
        setRoot(root)
        if (config.dataFormat != null) {
            setupDragging()
        }
    }

    private fun createSubWindow(): SubWindow? {
        content = config.getContent(obj, DisplayMode.SubWindow) ?: return null
        val objectType = parent.source.objectType
        val name = if (obj is NamedObject) obj.name.now else ""
        val title = "$objectType $name"
        val context = if (obj is ContextualObject) obj.context else parent.source.context
        val window =
            if (obj is NamedObject) makeSubWindow(obj, content!!)
            else makeSubWindow(content!!, title, context).also { it.sizeToScene() }
        config.configureSubWindow(window, obj)
        window.initOwner(context[primaryStage])
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
        val dragTarget = config.getDragTarget(this)
        if (dragTarget == prevDragTarget) return
        prevDragTarget = dragTarget
        dragTarget.setOnDragDetected { ev ->
            val db = if (ev.isControlDown && config.canCopy(obj)) this.startDragAndDrop(TransferMode.COPY)
            else this.startDragAndDrop(TransferMode.MOVE, TransferMode.LINK)
            if (obj is NamedObject) {
                db.setContent(mapOf(config.dataFormat to obj.name.now))
            }
            config.configureDragboard(obj, db)
            parent.startDrag(obj)
            ev.consume()
        }
        setOnDragDone { ev ->
            if (!ev.isDropCompleted && config.hideWhileDragging) {
                isVisible = true
                isManaged = true
            }
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        val objectActions = collectActions<ObjectBox<*>> {
            addAction("Edit object details") {
                icon { box ->
                    val config = box.config as ObjectListDisplayConfig<Any>
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
        }
    }
}
