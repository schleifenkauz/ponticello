package xenakis.ui.registry

import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.registerShortcuts
import fxutils.infiniteSpace
import fxutils.label
import fxutils.setupWindowDragging
import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

abstract class ToolPane : VBox() {
    private lateinit var content: Node
    private var headerContent: Node? = null
    lateinit var header: Region
        private set

    fun setup(
        title: ReactiveString, content: Node,
        headerContent: Node? = null, actions: List<ContextualizedAction> = emptyList()
    ) {
        val headerActions = actions + ToolPane.actions.withContext(this)
        this.headerContent = headerContent
        this.content = content
        header = createHeader(title, headerActions)
        children.addAll(header, content)
        registerShortcuts(headerActions)
    }

    fun setup(
        title: String, content: Node,
        headerContent: Node? = null, actions: List<ContextualizedAction> = emptyList()
    ) {
        setup(reactiveValue(title), content, headerContent, actions)
    }

    override fun requestFocus() {
        headerContent?.requestFocus() ?: content.requestFocus()
    }

    private fun createHeader(title: ReactiveString, headerActions: List<ContextualizedAction>): HBox {
        val label = label(title).styleClass("heading")
        val actionBar = ActionBar(headerActions, buttonStyle = "medium-icon-button")
        val box = HBox(label, infiniteSpace(), actionBar).styleClass("tool-pane-header")
        box.setupWindowDragging { scene.window }
        label.setupWindowDragging { scene.window }
        if (headerContent != null) box.children.add(1, headerContent)
        return box
    }

    companion object {
        private val actions = collectActions<ToolPane> {
            addAction("Close window") {
                shortcut("Ctrl+W")
                icon(MaterialDesignC.CLOSE)
                executes { p -> p.scene.window.hide() }
            }
        }
    }
}