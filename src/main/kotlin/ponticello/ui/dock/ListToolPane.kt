package ponticello.ui.dock

import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.registerActions
import fxutils.addAfter
import fxutils.registerShortcuts
import javafx.scene.Parent
import javafx.stage.Window
import ponticello.model.obj.ContextualObject
import ponticello.model.registry.ObjectList
import ponticello.ui.registry.ListDisplayConfig
import ponticello.ui.registry.ObjectListView
import ponticello.ui.registry.ObjectListView.Companion.modeChangeActions
import reaktive.value.now

abstract class ListToolPane<O : ContextualObject>(
    private val list: ObjectList<O>, private val scrollable: Boolean = true,
): ToolPane(), ListDisplayConfig<O> {
    lateinit var listView: ObjectListView<O>
        private set

    override val headerActions: List<ContextualizedAction>
        get() = modeChangeActions.withContext(listView) + super.headerActions

    override val content: Parent get() = listView

    override fun doSetup() {
        val state = initialState
        var initialMode = supportedModes.first()
        if (state is ListToolPaneState) {
            if (state.displayMode != null) initialMode = state.displayMode!!
        }
        listView = ObjectListView(list, this, scrollable, initialMode)
        if (state is ListToolPaneState) {
            for (idx in state.expandedBoxes) {
                val box = listView.getBoxes().getOrNull(idx)
                box?.toggleExpanded()
            }
        }
    }

    override fun afterSetup() {
        val actions = listView.actions + extraHeaderActions()
        registerShortcuts {
            registerActions(actions)
        }
        header.children.addAfter(headerContent, ActionBar(actions, buttonStyle = "medium-icon-button"))
    }

    fun showContent(obj: O): Window? {
        if (listView.mode.now != ObjectListView.DisplayMode.SubWindow) {
            setShowing(true)
        }
        return listView.showContent(obj)
    }

    protected open fun extraHeaderActions(): List<ContextualizedAction> = emptyList()

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is ListToolPaneState && isSetup) {
            dest.displayMode = listView.mode.now
            dest.expandedBoxes = listView.getBoxes().withIndex()
                .filter { (_, box) -> box.isExpanded }
                .map(IndexedValue<*>::index)
        }
    }
}