package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.styleClass
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.value.now
import xenakis.model.registry.NamedObject
import xenakis.model.registry.NamedObjectList

abstract class SearchableToolPane<O : NamedObject>() : ToolPane(), ObjectBoxConfig<O> {
    protected val searchText = CustomTextField().styleClass("sleek-text-field", "search-field")

    lateinit var listView: NamedObjectListView<O>
        private set

    protected fun setup(title: String, list: NamedObjectList<O>, actions: () -> List<ContextualizedAction>) {
        listView = NamedObjectListView(list, this)
        setupSearchField()
        setup(title, listView, searchText, actions())
    }

    private fun setupSearchField() {
        listView.setFilter { obj -> filter(obj) && matchesSearch(obj) }
        searchText.promptText = "Search..."
        searchText.left = FontIcon(Material2MZ.SEARCH)
        searchText.textProperty().addListener { _, _, _ -> listView.refilter() }
        HBox.setHgrow(searchText, Priority.ALWAYS)
    }

    protected open fun filter(obj: O): Boolean = true

    private fun matchesSearch(obj: O) = obj.name.now.contains(searchText.text, ignoreCase = true)
}