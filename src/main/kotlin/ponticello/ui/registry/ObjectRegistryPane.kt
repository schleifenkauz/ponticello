package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.plural
import fxutils.styleClass
import fxutils.undo.UndoManager
import javafx.event.Event
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.impl.Logger
import ponticello.model.registry.NamedObject
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.actions.UndoRedoActions
import ponticello.ui.controls.NamePrompt
import reaktive.value.reactiveValue

abstract class ObjectRegistryPane<O : NamedObject>(
    val registry: ObjectRegistry<O>,
) : SearchableToolPane<O>() {
    init {
        styleClass("object-registry-pane")
    }

    protected open fun headerActions(): List<ContextualizedAction> = emptyList()

    protected fun setup() {
        setup(title = null, registry) { headerActions.withContext(this) + headerActions() }
        listView.autoResizeScene = true
    }

    protected open fun addObject(ev: Event?) {
        val name = NamePrompt(
            registry, "Name for new ${registry.objectType}",
            initialName = ""
        ).showDialog(ev) ?: return
        val obj = createNewObject(name, ev) ?: return
        registry.add(obj)
    }

    protected abstract fun createNewObject(name: String, ev: Event?): O?

    companion object {
        private val headerActions = collectActions<ObjectRegistryPane<*>> {
            addAll(UndoRedoActions) { pane -> pane.registry.context[UndoManager] }
            addAction("Create object") {
                description { p -> reactiveValue("Create new ${p.registry.objectType}") }
                shortcut("Ctrl+PLUS")
                icon(MaterialDesignP.PLUS)
                executes { p, ev -> p.addObject(ev) }
            }
            addAction("Sync registry") {
                description { p -> reactiveValue("Sync ${plural(p.registry.objectType)}") }
                shortcut("Ctrl+Shift+U")
                icon(MaterialDesignS.SYNC)
                executes { p ->
                    p.registry.syncAll()
                    p.registry.save()
                    Logger.confirm(
                        "Synchronized ${plural(p.registry.objectType)} with server and saved to project directory",
                        Logger.Category.Registries
                    )
                }
            }
        }
    }
}