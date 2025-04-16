package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.registerShortcuts
import fxutils.plural
import fxutils.styleClass
import javafx.geometry.Point2D
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import reaktive.value.reactiveValue
import xenakis.impl.Logger
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.controls.NamePrompt

abstract class ObjectRegistryPane<O : NamedObject>(
    val registry: ObjectRegistry<O>,
) : SearchableToolPane<O>() {
    init {
        styleClass("object-registry-pane")
    }

    protected open fun headerActions(): List<ContextualizedAction> = emptyList()

    protected fun setup(title: String = plural(registry.objectType)) {
        setup(title, registry) { headerActions() + headerActions.withContext(this) }
        listView.autoResizeScene = true
        registerShortcuts(listView.actions)
    }

    protected open fun addObject() {
        val name = NamePrompt(
            registry, "Name for new ${registry.objectType}",
            initialName = ""
        ).showDialog(actionBar, offset = Point2D(0.0, actionBar.height)) ?: return
        val obj = createNewObject(name) ?: return
        registry.add(obj)
    }

    protected abstract fun createNewObject(name: String): O?

    companion object {
        private val headerActions = collectActions<ObjectRegistryPane<*>> {
            addAction("Create object") {
                description { p -> reactiveValue("Create new ${p.registry.objectType}") }
                shortcut("Ctrl+PLUS")
                icon(MaterialDesignP.PLUS)
                executes { p -> p.addObject() }
            }
            addAction("Sync registry") {
                description { p -> reactiveValue("Sync ${plural(p.registry.objectType)}") }
                shortcut("Ctrl+Shift+S")
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
            addAll(NamedObjectListView.modeChangeActions) { p -> p.listView }
        }
    }
}