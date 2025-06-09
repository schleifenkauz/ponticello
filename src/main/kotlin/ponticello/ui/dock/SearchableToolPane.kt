package ponticello.ui.dock

import fxutils.actions.*
import fxutils.styleClass
import javafx.scene.Cursor
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.model.registry.NamedObject
import ponticello.model.registry.NamedObjectList
import ponticello.ui.registry.ObjectListDisplayConfig
import ponticello.ui.registry.ObjectListView
import ponticello.ui.registry.ObjectListView.Companion.modeChangeActions
import reaktive.value.now

abstract class SearchableToolPane<O : NamedObject>(
    private val list: NamedObjectList<O>,
) : ToolPane(), ObjectListDisplayConfig<O> {
    protected val searchText = CustomTextField().styleClass("sleek-text-field", "search-field")

    lateinit var listView: ObjectListView<O>
        private set

    override val headerActions: List<ContextualizedAction>
        get() = modeChangeActions.withContext(listView) + fitContentAction.withContext(this)

    override val content: ObjectListView<O> get() = listView

    override fun doSetup() {
        listView = ObjectListView(list, this, filter = { obj -> filter(obj) && matchesSearch(obj) })
        setupSearchField()
    }

    override fun afterSetup() {
        val actions = listView.actions + extraHeaderActions() + headerActions
        registerShortcuts(actions)
        header.children.add(1, ActionBar(actions, buttonStyle = "medium-icon-button"))
    }

    protected open fun extraHeaderActions(): List<ContextualizedAction> = emptyList()

    private fun setupSearchField() {
        searchText.promptText = "Search..."
        searchText.maxWidth = 150.0
        searchText.left = FontIcon(Material2MZ.SEARCH)
        searchText.right = MaterialDesignC.CLOSE_CIRCLE_OUTLINE.button("Clear search", "small-icon-button") {
            searchText.text = ""
        }.also { btn -> btn.cursor = Cursor.DEFAULT }
        searchText.right.setOnMouseClicked { searchText.text = "" }
        searchText.textProperty().addListener { _, _, _ -> listView.refilter() }
        searchText.setOnAction { ev ->
            if (ev.target == searchText) listView.showSelected()
        }
    }

    protected open fun filter(obj: O): Boolean = true

    private fun matchesSearch(obj: O) = obj.name.now.contains(searchText.text, ignoreCase = true)

    companion object {
        private val actions = collectActions {
            addAction("Focus search field") {
                shortcut("Ctrl+F")
                executes { pane: SearchableToolPane<*> ->
                    pane.searchText.requestFocus()
                }
            }
        }
    }
}