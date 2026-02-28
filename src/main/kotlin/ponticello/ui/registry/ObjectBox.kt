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
import ponticello.model.obj.NamedObject
import ponticello.model.obj.RenamableObject
import ponticello.model.registry.reference
import ponticello.ui.controls.NameControl
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.binding.and
import reaktive.value.binding.equalTo
import reaktive.value.binding.`if`
import reaktive.value.binding.impl.notNull
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable
import reaktive.value.toggle

class ObjectBox<O : Any>(val parent: ObjectListView<O>, val obj: O) : Control() {
    var subWindow: SubWindow? = null
        private set

    private var prevDragTarget: Node? = null

    val currentMode get() = parent.mode.now

    val config get() = parent.config

    val nameControl = if (obj is RenamableObject && obj.canRename) NameControl(obj) else null

    val nameLabel: Label?

    private val expanded = reactiveVariable(false)

    private val contentUpdateObserver =
        config.contentUpdate(obj)?.observe { _ -> content = config.getContent(obj, this) }

    val isExpanded get() = expanded.now

    val isCollapsed by lazy {
        parent.mode.equalTo(DisplayMode.Collapsable) and _content.notNull() and !expanded
    }

    private val nameDisplay: Region? = when {
        nameControl != null -> {
            nameLabel = nameControl.label
            nameControl.setFixedWidth(config.nameDisplayWidth)
        }

        obj is NamedObject -> {
            nameLabel = Label(obj.name.now).styleClass("name-field")
            nameLabel.setFixedWidth(config.nameDisplayWidth)
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
        private set(value) {
            _content.now = value
            if (value != null) {
                val visible = (!isCollapsed).asObservableValue()
                value.visibleProperty().bind(visible)
                value.managedProperty().bind(visible)
            }
            if (currentMode is DisplayMode.Inline) {
                relayout()
            }
        }

    fun content(): Parent? {
        if (content == null) {
            content = config.getContent(obj, this)
        }
        return content
    }

    init {
        isFocusTraversable = true
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            parent.select(this)
            if (ev.clickCount == 2 && currentMode == DisplayMode.Inline && content != null) {
                toggleExpanded()
            }
            ev.consume()
        }
        styleClass(*config.boxStyle)
    }

    fun updateMode(oldMode: DisplayMode?, newMode: DisplayMode) {
        if (newMode != DisplayMode.SubWindow) {
            subWindow?.let { w ->
                w.hide()
                w.scene.root = Region()
                subWindow = null
            }
        }
        content =
            if (newMode is DisplayMode.Inline) config.getContent(obj, this)
            else null
        relayout()
        if (config.dataFormat != null) {
            setupDragging()
        }
        config.configureBox(this, currentMode)
    }

    private fun relayout() {
        val root =
            if (currentMode == DisplayMode.Collapsable && content != null && !expanded.now)
                config.collapsedLayout(this, header, content)
            else
                config.boxLayout(obj, header, content)
        updateInlineContentPseudoClass()
        setRoot(root)
    }

    private fun updateInlineContentPseudoClass() {
        val inlineContent = content != null && currentMode == DisplayMode.Inline && (content == null || expanded.now)
        setPseudoClassState("inline-content", inlineContent)
    }

    private fun createSubWindow(): SubWindow? {
        content = config.getContent(obj, this) ?: return null
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
        w.minWidth = 100.0
        w.minHeight = 100.0
        w.showAndBringToFront()
        return w
    }

    private fun setupDragging() {
        val dragTarget = config.getDragTarget(this)
        if (dragTarget == prevDragTarget) return
        prevDragTarget = dragTarget
        dragTarget.setOnDragDetected { ev ->
            val db =
                if (ev.isControlDown && obj is NamedObject && config.canDuplicate) this.startDragAndDrop(TransferMode.COPY)
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
