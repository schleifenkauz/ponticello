package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.styleClass
import ponticello.model.registry.NamedObject
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.dock.SearchableToolPane

abstract class ObjectRegistryPane<O : NamedObject>(val registry: ObjectRegistry<O>) : SearchableToolPane<O>(registry) {
    init {
        styleClass("object-registry-pane")
    }

    override fun afterSetup() {
        super.afterSetup()
        listView.autoResizeScene = true
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