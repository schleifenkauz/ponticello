package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.drag.hasFiles
import fxutils.styleClass
import hextant.serial.readJson
import javafx.scene.input.DragEvent
import kotlinx.serialization.KSerializer
import ponticello.impl.Logger
import ponticello.model.project.ComponentSerializer
import ponticello.model.project.MultiFileComponentSerializer
import ponticello.model.registry.NamedObject
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.dock.SearchableToolPane

abstract class ObjectRegistryPane<O : NamedObject>(
    val registry: ObjectRegistry<O>,
    private val serializer: ComponentSerializer<*>? = null
) : SearchableToolPane<O>(registry) {
    init {
        styleClass("object-registry-pane")
    }

    override fun afterSetup() {
        super.afterSetup()
        listView.autoResizeScene = true
    }

    override fun getDroppedObjects(ev: DragEvent, targetView: ObjectListView<O>): List<O> {
        if (serializer !is MultiFileComponentSerializer<*, *> || serializer.extension == "json") return emptyList()
        if (ev.dragboard.hasFiles(serializer.extension)) {
            return ev.dragboard.files.mapNotNull { file ->
                if (registry.has(name = file.nameWithoutExtension)) null
                else try {
                    @Suppress("UNCHECKED_CAST")
                    file.readJson(serializer.itemSerializer as KSerializer<O>)
                } catch (ex: Exception) {
                    Logger.error("Error parsing $file", ex)
                    null
                }
            }
        }
        return emptyList()
    }

    override fun extraHeaderActions(): List<ContextualizedAction> = actions.withContext(this)

    companion object {
        private val actions = collectActions<ObjectRegistryPane<*>> {
//            addAll(UndoRedoActions) { pane -> pane.registry.context[UndoManager] }
//            addAction("Sync object") {
//                description { p -> reactiveValue("Sync ${plural(p.registry.objectType)}") }
//                icon(MaterialDesignS.SYNC)
//                enableWhen { p ->
//                    p.listView.selectedBox().map { box ->
//                        box != null && box.obj is SuperColliderObject
//                    }
//                }
//                executes { p ->
//                    val selected = p.listView.selectedObject() ?: return@executes
//                    if (selected is SuperColliderObject) {
//                        selected.sync()
//                    }
//                    Logger.confirm(
//                        "Synchronized ${plural(p.registry.objectType)} with server and saved to project directory",
//                        Logger.Category.Registries
//                    )
//                }
//            }
        }
    }
}