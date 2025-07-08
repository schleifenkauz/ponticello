package ponticello.ui.registry

import fxutils.*
import fxutils.actions.Action
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.model.obj.ContextualObject
import ponticello.model.obj.RenamableObject
import ponticello.model.registry.NamedObject
import ponticello.model.registry.reference
import ponticello.ui.controls.NameControl
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.binding.*
import reaktive.value.binding.impl.notNull
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable
import reaktive.value.toggle

class ObjectBox<O : Any>(val parent: ObjectListView<O>, val obj: O) : Control() {
    var subWindow: SubWindow? = null
        private set

    private var prevDragTarget: Node? = null

    private val currentMode get() = parent.mode.now

    val config get() = parent.config

    val nameControl = if (obj is RenamableObject) NameControl(obj) else null

    val nameLabel: Label?

    private val expanded = reactiveVariable(false)

    val isExpanded get() = expanded.now

    val isCollapsed by lazy {
        binding(parent.mode, expanded) { mode, expanded ->
            mode == DisplayMode.Collapsable && !expanded
        }
    }

    private val nameDisplay: Region? = when {
        nameControl != null -> {
            nameLabel = nameControl.label
            nameControl.setFixedWidth(150.0)
        }

        obj is NamedObject -> {
            nameLabel = Label(obj.name.now).styleClass("name-field")
            HBox(nameLabel).styleClass("name")
        }

        else -> {
            nameLabel = null
            null
        }
    }

    val actionBar by lazy {
        ActionBar(
            config.getActions(this) + objectActions.withContext(this),
            config.buttonStyle
        )
    }

    val header by lazy {
        val box = HBox() styleClass "object-box-header"
        if (nameDisplay != null) box.children.add(nameDisplay)
        box.children.addAll(config.getHeaderContent(obj))
        if (config.addSpaceBeforeActionBar) box.children.add(infiniteSpace())
        box.children.add(actionBar)
        box
    }

    private var _content = reactiveVariable<Parent?>(null)

    var content: Parent?
        get() = _content.now
        set(value) {
            _content.now = value
            if (value != null) {
                val visible = (parent.mode.notEqualTo(DisplayMode.Collapsable) or expanded)
                    .asObservableValue()
                value.visibleProperty().bind(visible)
                value.managedProperty().bind(visible)
            }
        }

    fun content(): Parent? {
        content?.let { return it }
        content = (config.getContent(obj, currentMode) ?: return null)
        return content!!
    }

    init {
        isFocusTraversable = true
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            parent.select(this)
            if (ev.clickCount == 2 && currentMode == DisplayMode.Collapsable) {
                toggleExpanded()
            }
            ev.consume()
        }
        styleClass(*config.boxStyle)
    }

    fun updateMode() {
        content = null
        if (currentMode != DisplayMode.SubWindow) {
            subWindow?.let { w ->
                w.hide()
                w.scene.root = Region()
                subWindow = null
            }
        }
        if (currentMode is DisplayMode.Inline) {
            content = config.getContent(obj, currentMode)
        }
        relayout()
        if (config.dataFormat != null) {
            setupDragging()
        }
        config.configureBox(this, currentMode)
    }

    private fun relayout() {
        val root =
            if (currentMode == DisplayMode.Collapsable && !expanded.now) config.collapsedLayout(this, header, content)
            else config.boxLayout(obj, header, content)
        updateInlineContentPseudoClass()
        setRoot(root)
    }

    private fun updateInlineContentPseudoClass() {
        val inlineContent = content != null && currentMode == DisplayMode.Inline(collapsable = false)
                || currentMode == DisplayMode.Collapsable && expanded.now
        setPseudoClassState("inline-content", inlineContent)
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

    fun toggleExpanded() {
        expanded.toggle()
        relayout()
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
                db.setContent(mapOf(config.dataFormat to obj.reference()))
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
            addAction("Show in sub-window") {
                icon { box ->
                    val config = box.config as ListDisplayConfig<Any>
                    reactiveValue(config.detailWindowIcon(box.obj))
                }
                shortcuts("Ctrl+E")
                enableWhen { box -> box.parent.mode.equalTo(DisplayMode.SubWindow) }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                executes { box, _ ->
                    box.showSubWindow()
                }
            }
            addAction("Expand/Collapse") {
                description { box ->
                    `if`(box.expanded, then = { "Collapse" }, otherwise = { "Expand" })
                }
                icon { box ->
                    `if`(
                        box.expanded,
                        then = { MaterialDesignC.CHEVRON_UP },
                        otherwise = { MaterialDesignC.CHEVRON_DOWN }
                    )
                }
                enableWhen { box ->
                    box.parent.mode.equalTo(DisplayMode.Collapsable) and box._content.notNull()
                }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                executes { box, _ -> box.toggleExpanded() }
            }
        }
    }
}
