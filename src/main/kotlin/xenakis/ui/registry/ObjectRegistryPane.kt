package xenakis.ui.registry

import fxutils.*
import fxutils.actions.ContextualizedAction
import fxutils.actions.button
import fxutils.actions.collectActions
import javafx.collections.FXCollections
import javafx.scene.Node
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import org.controlsfx.control.SearchableComboBox
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import reaktive.value.ReactiveString
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.Logger
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.Identifier
import xenakis.ui.controls.NamePrompt

abstract class ObjectRegistryPane<O : NamedObject>(
    private val registry: ObjectRegistry<O>
) : ToolPane(), ObjectRegistry.Listener<O>, ObjectBoxType<O> {
    protected val searchText = CustomTextField().styleClass("sleek-text-field", "search-field")
    protected val boxList: ObjectBoxList<O> = ObjectBoxList(registry.all(), this)

    init {
        setupSearchField()
    }

    private fun setupSearchField() {
        boxList.setFilter(::matchesSearch)
        searchText.promptText = "Search..."
        searchText.left = FontIcon(Material2MZ.SEARCH)
        searchText.textProperty().addListener { _, _, _ -> boxList.layoutBoxes() }
        HBox.setHgrow(searchText, Priority.ALWAYS)
    }

    override fun getTitle(): ReactiveString = reactiveValue(plural(registry.objectType))

    override fun getHeaderActions(): List<ContextualizedAction> = actions.withContext(this)

    override fun getHeaderContent(): Node = searchText

    override fun getContent(): Node = boxList

    override fun deleteObject(obj: O) {
        registry.remove(obj)
    }

    protected abstract fun sync()

    protected open fun addObject() {
        val name = NamePrompt(registry, "Name for new ${registry.objectType}", initialName = searchText.text)
            .showDialog(anchorNode = this) ?: return
        addObject(name)
    }

    private fun matchesSearch(obj: O) = obj.name.now.contains(searchText.text, ignoreCase = true)

    override fun added(obj: O, idx: Int) {
        boxList.add(idx, obj)
    }

    override fun removed(obj: O, idx: Int) {
        boxList.remove(obj)
    }

    protected abstract fun addObject(name: String): O?

    protected fun <T : Any> showCreateNewDialog(options: List<T>, default: T, createObject: (T, String) -> O?) {
        val typeSelector = SearchableComboBox(FXCollections.observableList(options))
        typeSelector.value = default
        val nameInput = TextField() styleClass "prompt-text-field"
        nameInput.promptText = "${registry.objectType} name"
        val ok = Material2AL.CHECK.button(action = "Confirm").styleClass("medium-icon-button")
        val layout = HBox(typeSelector, nameInput).centerChildren() styleClass "prompt"
        val window = SubWindow(
            layout, "Create new ${registry.objectType}",
            type = SubWindow.Type.Popup
        )
        window.initOwner(scene.window)
        window.setOnShown { nameInput.requestFocus() }
        fun commit() {
            val type = typeSelector.value ?: return
            val name = nameInput.text
            if (!Identifier.isValid(name) || registry.has(name)) return
            window.hide()
            val obj = createObject(type, name) ?: return
            registry.add(obj)
        }
        ok.setOnAction { commit() }
        layout.registerShortcuts {
            on("ENTER") { commit() }
        }
        window.sizeToScene()
        window.show()
    }

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