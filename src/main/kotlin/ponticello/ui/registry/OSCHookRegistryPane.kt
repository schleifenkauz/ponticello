package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.PromptPlacement
import fxutils.styleClass
import hextant.serial.EditorRoot
import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.input.DataFormat
import javafx.scene.input.MouseButton
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.model.code.OSCHookObject
import ponticello.model.code.OSCHookRegistry
import ponticello.model.obj.SuperColliderObject
import ponticello.model.project.OSC_HOOKS
import ponticello.model.project.PonticelloProject
import ponticello.model.project.get
import ponticello.sc.editor.ScExprEditor
import ponticello.ui.dock.ListToolPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.binding.`if`
import reaktive.value.forEach
import reaktive.value.now

class OSCHookRegistryPane(
    registry: OSCHookRegistry
) : CodeObjectRegistryPane<OSCHookObject>(registry, OSC_HOOKS.serializer) {
    override val type: Type get() = OSCHookRegistryPane

    override val canDuplicate: Boolean get() = true
    override val canCreateNewObject: Boolean get() = true
    override val supportedModes: Collection<DisplayMode>
        get() = setOf(DisplayMode.Collapsable, DisplayMode.DetailsPane)

    override val dataFormat: DataFormat
        get() = OSCHookObject.DATA_FORMAT

    override fun detailWindowIcon(obj: OSCHookObject): Ikon = Material2AL.CODE

    override fun defaultState(): ToolPaneState = ListToolPaneState.docked

    override fun createNewObject(name: String, promptPlacement: PromptPlacement?): OSCHookObject =
        OSCHookObject.create(name)

    override fun getEditorRoot(obj: OSCHookObject): EditorRoot<out ScExprEditor<*>> = obj.function

    override fun getActions(box: ObjectBox<OSCHookObject>): List<ContextualizedAction> =
        actions.withContext(box.obj) +
                SuperColliderObject.actions.withContext(box.obj) +
                ObjectBox.removeObjectAction.withContext(box)

    override fun getHeaderContent(obj: OSCHookObject): List<Node> {
        val btn = Button() styleClass "event-count-button"
        btn.userData = obj.eventCount.forEach { count ->
            Platform.runLater { btn.text = count.toString() }
        }
        btn.isFocusTraversable = false
        btn.setOnMouseClicked { ev ->
            when (ev.button) {
                MouseButton.PRIMARY -> showEventTable(obj)
                MouseButton.SECONDARY -> obj.resetEvents()
                else -> return@setOnMouseClicked
            }
            ev.consume()
        }
        return listOf(btn)
    }

    private fun showEventTable(obj: OSCHookObject) {
        val table = TableView(FXCollections.observableArrayList(obj.events))
        val timeColumn = TableColumn<OSCHookObject.Event, String>("Timestamp")
        timeColumn.setCellValueFactory { cell -> SimpleObjectProperty(cell.value.timestamp.toString()) }
        val addressColumn = TableColumn<OSCHookObject.Event, String>("Address")
        addressColumn.setCellValueFactory { cell ->
            val addr = "${cell.value.hostname}:${cell.value.port}"
            SimpleObjectProperty(addr)
        }
        val columns = mutableListOf(timeColumn, addressColumn)
        for ((idx, param) in obj.function.editor.result.now.parameters.withIndex()) {
            val column = TableColumn<OSCHookObject.Event, String>(param.text)
            column.setCellValueFactory { cell -> SimpleObjectProperty(cell.value.arguments.getOrNull(idx)) }
            columns.add(column)
        }
        table.columns.addAll(columns)
        val window = makeSubWindow(table, "Events for /${obj.name.now}", obj.context)
        window.show()
    }

    companion object : Type(uid = 20, "OSC Hooks") {
        private val actions = collectActions<OSCHookObject> {
            addAction("Toggle enabled") {
                icon { obj ->
                    `if`(
                        obj.isEnabled,
                        then = { MaterialDesignR.RADIOBOX_MARKED },
                        otherwise = { MaterialDesignR.RADIOBOX_BLANK }
                    )
                }
                executes(OSCHookObject::toggleEnabled)
            }
        }

        override val defaultSide: Side
            get() = Side.RIGHT

        override val icon: Ikon get() = MaterialDesignA.ACCESS_POINT

        override fun createToolPane(project: PonticelloProject): ToolPane = OSCHookRegistryPane(project[OSC_HOOKS])
    }
}