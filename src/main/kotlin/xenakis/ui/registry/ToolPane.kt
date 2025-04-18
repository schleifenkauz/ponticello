package xenakis.ui.registry

import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.action
import fxutils.actions.registerShortcuts
import fxutils.infiniteSpace
import fxutils.label
import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority.ALWAYS
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.ReactiveString
import reaktive.value.binding.Binding
import reaktive.value.binding.equalTo
import reaktive.value.binding.flatMap
import reaktive.value.fx.asReactiveValue
import reaktive.value.reactiveValue

abstract class ToolPane : VBox() {
    lateinit var content: Node
        private set
    private var headerContent: Node? = null
    var header: Region? = null
        private set

    lateinit var actionBar: ActionBar
        private set

    init {
        styleClass("tool-pane")
    }

    fun setup(
        title: ReactiveString?, content: Node,
        headerContent: Node? = null, actions: List<ContextualizedAction> = emptyList(),
    ) {
        this.headerContent = headerContent
        this.content = content
        header = createHeader(title, actions)
        if (header != null) children.add(header)
        children.add(content)
        setVgrow(content, ALWAYS)
        registerShortcuts(actions)
    }

    fun setup(
        content: Node, title: String? = null,
        headerContent: Node? = null, actions: List<ContextualizedAction> = emptyList(),
    ) {
        setup(title?.let(::reactiveValue), content, headerContent, actions)
    }

    override fun requestFocus() {
        headerContent?.requestFocus() ?: content.requestFocus()
    }

    private fun createHeader(title: ReactiveString?, headerActions: List<ContextualizedAction>): HBox? {
        if (title == null && headerActions.isEmpty() && headerContent == null) return null
        actionBar = ActionBar(headerActions, buttonStyle = "medium-icon-button")
        val box = HBox(infiniteSpace(), actionBar).styleClass("tool-pane-header")
        if (headerContent != null) box.children.add(0, headerContent)
        if (title != null) {
            val label = label(title).styleClass("heading")
            box.children.add(0, label)
        }
        return box
    }

    companion object {
        private fun isSceneRoot(node: Node): Binding<Boolean> = node.sceneProperty().asReactiveValue().flatMap { s ->
            s?.rootProperty()?.asReactiveValue()?.equalTo(node) ?: reactiveValue(false)
        }

        val fitContentAction = action<Node>("Resize window to fit contents") {
            shortcut("Ctrl+L")
            applicableIf { p -> isSceneRoot(p) }
            icon(MaterialDesignR.RESIZE)
            executes { p -> p.scene.window.sizeToScene() }
        }
    }
}