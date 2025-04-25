package xenakis.ui.registry

import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.registerShortcuts
import fxutils.styleClass
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.registry.NamedObject
import xenakis.model.registry.NamedObjectList
import xenakis.ui.registry.NamedObjectListView.Companion.modeChangeActions

abstract class SearchableToolPane<O : NamedObject> : ToolPane(), NamedObjectListConfig<O> {
    protected val searchText = CustomTextField().styleClass("sleek-text-field", "search-field")

    lateinit var listView: NamedObjectListView<O>
        private set

    protected fun setup(
        title: String?, list: NamedObjectList<O>,
        extraActions: () -> List<ContextualizedAction> = { emptyList() },
    ) {
        listView = NamedObjectListView(list, this, filter = { obj -> filter(obj) && matchesSearch(obj) })
        setupSearchField()
        val windowActions = modeChangeActions.withContext(listView) + fitContentAction.withContext(this)
        setup(listView, title?.let(::reactiveValue), searchText, windowActions)
        val actions = listView.actions + extraActions()
        registerShortcuts(actions)
        header!!.children.add(1, ActionBar(actions, buttonStyle = "medium-icon-button"))
    }

    private fun setupSearchField() {
        searchText.promptText = "Search..."
        searchText.left = FontIcon(Material2MZ.SEARCH)
        searchText.textProperty().addListener { _, _, _ -> listView.refilter() }
        searchText.setOnAction {
            listView.showSelected()
        }
        HBox.setHgrow(searchText, Priority.ALWAYS)
    }

    protected open fun filter(obj: O): Boolean = true

    private fun matchesSearch(obj: O) = obj.name.now.contains(searchText.text, ignoreCase = true)

    companion object {
        val actions = collectActions {
            addAction("Focus search field") {
                shortcut("Ctrl+F")
                executes { pane: SearchableToolPane<*> ->
                    pane.searchText.requestFocus()
                }
            }
        }
    }
}