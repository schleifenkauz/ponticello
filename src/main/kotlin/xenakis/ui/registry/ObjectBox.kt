package xenakis.ui.registry

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
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
import xenakis.model.obj.RenamableObject
import xenakis.model.registry.NamedObject
import xenakis.model.registry.NamedObjectList
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.controls.NameControl
import xenakis.ui.controls.NamePrompt
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.registry.NamedObjectListView.DisplayMode

class ObjectBox<O : NamedObject>(val parent: NamedObjectListView<O>, val obj: O) : VBox() {
    var subWindow: SubWindow? = null
        private set

    val config get() = parent.config

    val nameControl = if (obj is RenamableObject) NameControl(obj, config.getDefaultDisplayName(obj)) else null

    private val nameDisplay =
        nameControl ?: HBox(label(obj.name).styleClass("name-field")).styleClass("name")

    val actionBar = ActionBar(
        config.getActions(this) + objectActions.withContext(this),
        config.buttonStyle
    )

    private val space = infiniteSpace()

    private val header = HBox(nameDisplay, *config.getItemContent(obj).toTypedArray(), space, actionBar)

    var content = config.getContent(obj)
        private set

    init {
        space.setOnMousePressed { parent.select(this) }
        addEventFilter(MouseEvent.MOUSE_PRESSED) { parent.select(this) }
        styleClass("object-box")
        children.add(header.styleClass("object-box-header"))
        if (config.enableReordering) setupReordering()
        if (config.dataFormat(obj) != null) setupDragging()
    }

    fun setContentDisplay(option: DisplayMode) {
        if (content == null) return
        if (option != DisplayMode.SubWindow) {
            subWindow?.let { w ->
                w.hide()
                w.scene.root = Region()
                subWindow = null
            }
        }
        if (option != DisplayMode.Inline && content in children) {
            children.remove(content)
        }
        if (option == DisplayMode.SubWindow) {
            val objectType = parent.source.objectType
            val name = obj.name.now
            val title = "$objectType $name"
            content = config.getContent(obj) ?: return //ugly trick to avoid weird JavaFX bug
            subWindow = makeSubWindow(content!!, title, parent.source.context).also { w ->
                config.configureSubWindow(w)
                w.sizeToScene()
                if (w.owner == null) w.initOwner(obj.context[primaryStage])
            }
        }
        if (option == DisplayMode.Inline) {
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
                    val config = box.config as ObjectBoxConfig<NamedObject>
                    reactiveValue(config.detailWindowIcon(box.obj))
                }
                shortcuts("Ctrl+E")
                applicableIf { box -> box.parent.mode.equalTo(DisplayMode.SubWindow) }
                executes { box, _ ->
                    box.showSubWindow()
                }
            }
            addAction("Duplicate object") {
                applicableIf { box -> reactiveValue(box.obj.canCopy && box.obj.registry != null) }
                icon(MaterialDesignC.CONTENT_DUPLICATE)
                description { box -> reactiveValue("Duplicate ${box.parent.source.objectType}") }
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
    }
}
