package ponticello.ui.dock

import fxutils.actions.ContextualizedAction
import fxutils.actions.button
import fxutils.actions.collectActions
import fxutils.styleClass
import javafx.scene.Cursor
import javafx.scene.Node
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignB
import ponticello.model.obj.NamedObject
import ponticello.model.registry.NamedObjectList
import ponticello.ui.registry.ObjectListView.Companion.modeChangeActions
import reaktive.value.now

abstract class SearchableToolPane<O : NamedObject>(
    list: NamedObjectList<O>, scrollable: Boolean = true,
) : ListToolPane<O>(list, scrollable) {
    protected val searchText = CustomTextField().styleClass("sleek-text-field", "search-field")

    override val headerContent: Node get() = searchText

    override val headerActions: List<ContextualizedAction>
        get() = modeChangeActions.withContext(listView) + actions.withContext(this) + ToolPane.actions.withContext(this)

    override fun doSetup() {
        super.doSetup()
        setupSearchField()
    }

    override fun filter(obj: O): Boolean = obj.name.now.contains(searchText.text, ignoreCase = true)

    private fun setupSearchField() {
        searchText.promptText = "Search..."
        searchText.maxWidth = 150.0
        searchText.left = FontIcon(Material2MZ.SEARCH)
        searchText.right = MaterialDesignB.BACKSPACE_OUTLINE.button("Clear search", "small-icon-button") {
            searchText.text = ""
        }.also { btn -> btn.cursor = Cursor.DEFAULT }
        searchText.right.setOnMouseClicked { searchText.text = "" }
        searchText.textProperty().addListener { _, _, _ -> listView.refilter() }
        searchText.setOnAction { ev ->
            if (ev.target == searchText) listView.showSelected()
        }
    }

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