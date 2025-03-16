package xenakis.ui.registry

import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.registerShortcuts
import fxutils.infiniteSpace
import fxutils.label
import fxutils.setupWindowDragButton
import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import reaktive.value.ReactiveString

abstract class ToolPane : VBox() {
    protected lateinit var header: HBox
        private set
    private var headerContent: Node? = null
    private lateinit var actions: List<ContextualizedAction>

    protected abstract fun getTitle(): ReactiveString

    protected open fun getHeaderContent(): Node? = null

    protected open fun getHeaderActions(): List<ContextualizedAction> = emptyList()

    protected abstract fun getContent(): Node

    fun initialize() {
        actions = ToolPane.actions.withContext(this) + getHeaderActions()
        headerContent = getHeaderContent()
        header = createHeader()
        children.addAll(header, getContent())
        registerShortcuts(actions)
    }

    override fun requestFocus() {
        headerContent?.requestFocus()
    }

    private fun createHeader(): HBox {
        val label = label(getTitle()).styleClass("heading")
        val actionBar = ActionBar(actions, buttonStyle = "medium-icon-button")
        val moveBtn = actionBar.getButton(ToolPane.actions.getAction("Move window"))
        moveBtn.setupWindowDragButton { scene.window }
        return HBox(label, headerContent, infiniteSpace(), actionBar).styleClass("tool-pane-header")
    }

    companion object {
        private val actions = collectActions<ToolPane> {
            addAction("Move window") {
                icon(MaterialDesignC.CURSOR_MOVE)
            }
            addAction("Close window") {
                shortcut("Ctrl+W")
                icon(MaterialDesignC.CLOSE)
                executes { p -> p.scene.window.hide() }
            }
        }
    }
}