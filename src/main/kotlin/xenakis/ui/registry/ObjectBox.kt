package xenakis.ui.registry

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import javafx.scene.Parent
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.binding.equalTo
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.obj.ContextualObject
import xenakis.model.obj.RenamableObject
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectList
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.controls.NameControl
import xenakis.ui.controls.NamePrompt
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.registry.ObjectListView.DisplayMode

class ObjectBox<O : ContextualObject>(val parent: ObjectListView<O>, val obj: O) : VBox() {
    var subWindow: SubWindow? = null
        private set

    val config get() = parent.config

    val nameControl = if (obj is RenamableObject) NameControl(obj, config.getDefaultDisplayName(obj)) else null

    private val nameDisplay =
        when {
            nameControl != null -> nameControl
            obj is NamedObject -> HBox(label(obj.name).styleClass("name-field")).styleClass("name")
            else -> null
        }

    val actionBar = ActionBar(
        config.getActions(this) + objectActions.withContext(this),
        config.buttonStyle
    )

    private val space = infiniteSpace()

    private val header = HBox() styleClass "object-box-header"

    var content: Parent? = null
        private set

    init {
        styleClass("object-box")
        if (nameDisplay != null) header.children.add(nameDisplay)
        header.children.addAll(config.getItemContent(obj))
        header.children.addAll(space, actionBar)
        if (config.enableReordering) setupReordering()
        if (obj is NamedObject && config.dataFormat(obj) != null) setupDragging()
        children.setAll(header)
        addEventFilter(MouseEvent.MOUSE_CLICKED) { ev ->
            parent.select(this)
            if (ev.clickCount == 2) {
                parent.showSelected()
            }
        }
    }

    fun setContentDisplay(option: DisplayMode) {
        if (option != DisplayMode.SubWindow) {
            subWindow?.let { w ->
                w.hide()
                w.scene.root = Region()
                subWindow = null
            }
        }
        if (option != DisplayMode.Inline) {
            children.setAll(header)
        }
        content = config.getContent(obj, option) ?: return
        if (option == DisplayMode.SubWindow) {
            val objectType = parent.source.objectType
            val name = if (obj is NamedObject) obj.name.now else ""
            val title = "$objectType $name"
            subWindow = makeSubWindow(content!!, title, parent.source.context).also { w ->
                config.configureSubWindow(w)
                w.sizeToScene()
                if (w.owner == null) w.initOwner(obj.context[primaryStage])
            }
        }
        if (option == DisplayMode.Inline) {
            children.setAll(header, content)
        }
    }

    fun showSubWindow() {
        val w = this.subWindow ?: return
        w.showOrBringToFront()
    }

    private fun setupDragging() {
        val dragTarget = space
        obj as NamedObject
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
                var idx = parent.getBoxes().binarySearchBy(layoutY + translateY) { b -> b.layoutY }
                if (idx < 0) idx = -(idx + 1)
                val oldIndex = parent.getBoxes().indexOf(this)
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

    companion object {
        @Suppress("UNCHECKED_CAST")
        private val objectActions = collectActions<ObjectBox<*>> {
            addAction("Edit object details") {
                icon { box ->
                    val config = box.config as ObjectListDisplayConfig<ContextualObject>
                    reactiveValue(config.detailWindowIcon(box.obj))
                }
                shortcuts("Ctrl+E")
                applicableWhen { box -> box.parent.mode.equalTo(DisplayMode.SubWindow) }
                executes { box, _ ->
                    box.showSubWindow()
                }
            }
            addAction("Duplicate object") {
                applicableIf { box -> box.obj is NamedObject && box.obj.canCopy && box.obj.registry != null }
                icon(MaterialDesignC.CONTENT_DUPLICATE)
                description { box -> reactiveValue("Duplicate ${box.parent.source.objectType}") }
                executes { box, ev ->
                    val obj = box.obj as NamedObject
                    val list = box.parent.source as ObjectRegistry<NamedObject>
                    val initialName = obj.name.now + "_copy"
                    val name = NamePrompt(list, "Name for new duplicate instrument", initialName)
                        .showDialog(ev) ?: return@executes
                    val copy = obj.copy(name)
                    list.add(copy, list.indexOf(obj) + 1)
                }
            }
            addAction("Reorder") {
                icon(MaterialDesignR.REORDER_HORIZONTAL)
                applicableIf { box -> box.config.enableReordering }
            }
            addAction("Delete object") {
                icon(Material2AL.DELETE)
                shortcuts("Ctrl+DELETE")
                applicableIf { box -> box.obj is NamedObject && box.obj.canDelete }
                executes { box ->
                    val source = box.parent.source as ObjectList<ContextualObject>
                    source.remove(box.obj)
                }
            }
        }
    }
}
