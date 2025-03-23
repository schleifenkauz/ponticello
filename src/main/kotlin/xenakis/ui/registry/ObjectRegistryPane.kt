package xenakis.ui.registry

import fxutils.actions.collectActions
import fxutils.actions.registerShortcuts
import fxutils.plural
import fxutils.styleClass
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.Logger
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.controls.NamePrompt

abstract class ObjectRegistryPane<O : NamedObject>(
    private val registry: ObjectRegistry<O>
) : ToolPane(), ObjectBoxConfig<O> {
    protected val searchText = CustomTextField().styleClass("sleek-text-field", "search-field")
    protected val boxList: NamedObjectListView<O> = NamedObjectListView(registry, this)

    init {
        setupSearchField()
        boxList.autoResizeScene = true
        setup(
            title = plural(registry.objectType),
            content = boxList, headerContent = searchText,
            actions.withContext(this)
        )
        registerShortcuts(boxList.actions)
    }

    private fun setupSearchField() {
        boxList.setFilter { obj -> filter(obj) && matchesSearch(obj) }
        searchText.promptText = "Search..."
        searchText.left = FontIcon(Material2MZ.SEARCH)
        searchText.textProperty().addListener { _, _, _ -> boxList.refilter() }
        HBox.setHgrow(searchText, Priority.ALWAYS)
    }

    protected abstract fun sync()

    protected open fun filter(obj: O): Boolean = true

    protected open fun addObject() {
        val name = NamePrompt(
            registry, "Name for new ${registry.objectType}",
            initialName = ""
        ).showDialog(anchorNode = this) ?: return
        addObject(name)
    }

    private fun matchesSearch(obj: O) = obj.name.now.contains(searchText.text, ignoreCase = true)

    protected abstract fun addObject(name: String): O?

    companion object {
        private val actions = collectActions<ObjectRegistryPane<*>> {
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
                    p.sync()
                    Logger.confirm(
                        "Synchronized ${plural(p.registry.objectType)} with server",
                        Logger.Category.Registries
                    )
                }
            }
        }
    }
}