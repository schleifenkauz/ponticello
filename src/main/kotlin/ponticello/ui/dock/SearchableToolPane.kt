package ponticello.ui.dock

import fxutils.actions.*
import fxutils.addAfter
import fxutils.styleClass
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.stage.Window
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignB
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
        get() = modeChangeActions.withContext(listView) + fitContentAction.withContext(this) + actions.withContext(this)

    override val content: ObjectListView<O> get() = listView
    override val headerContent: Node get() = searchText

    override fun doSetup() {
        var initialMode = supportedModes.first()
        val state = initialState
        if (state is SearchableToolPaneState) {
            if (state.displayMode != null) initialMode = state.displayMode!!
        }
        listView = ObjectListView(list, this, initialMode, filter = { obj -> filter(obj) && matchesSearch(obj) })
        if (state is SearchableToolPaneState) {
            for (idx in state.expandedBoxes) {
                listView.getBoxes()[idx].toggleExpanded()
            }
        }
        setupSearchField()
    }

    override fun afterSetup() {
        val actions = listView.actions + extraHeaderActions()
        registerShortcuts(actions)
        header.children.addAfter(headerContent, ActionBar(actions, buttonStyle = "medium-icon-button"))
    }

    fun showContent(obj: O): Window? {
        setShowing(true)
        return listView.showContent(obj)
    }

    protected open fun extraHeaderActions(): List<ContextualizedAction> = emptyList()

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

    protected open fun filter(obj: O): Boolean = true

    private fun matchesSearch(obj: O) = obj.name.now.contains(searchText.text, ignoreCase = true)

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is SearchableToolPaneState && isSetup) {
            dest.displayMode = listView.mode.now
            dest.expandedBoxes = listView.getBoxes().withIndex()
                .filter { (_, box) -> box.isExpanded.now }
                .map(IndexedValue<*>::index)
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