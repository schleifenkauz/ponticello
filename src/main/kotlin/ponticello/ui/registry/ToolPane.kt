package ponticello.ui.registry

import fxutils.actions.*
import fxutils.infiniteSpace
import fxutils.label
import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority.ALWAYS
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.ReactiveString
import reaktive.value.binding.Binding
import reaktive.value.binding.equalTo
import reaktive.value.binding.flatMap
import reaktive.value.fx.asReactiveValue
import reaktive.value.reactiveValue

abstract class ToolPane : VBox() {
    lateinit var title: ReactiveString
        private set
    lateinit var content: Node
        private set
    private var headerContent: Node? = null
    var header: HBox? = null
        private set

    var actionBar: ActionBar? = null
        private set

    init {
        styleClass("tool-pane")
    }

    fun setup(
        content: Node, title: ReactiveString? = null,
        headerContent: Node? = null, actions: List<ContextualizedAction> = emptyList(),
    ) {
        this.headerContent = headerContent
        this.content = content
        this.title = title ?: reactiveValue("<???>")
        header = createHeader(title, actions)
        if (header != null) children.add(header)
        children.add(content)
        setVgrow(content, ALWAYS)
        registerShortcuts(actions)
    }

    fun setup(
        content: Node, title: String,
        headerContent: Node? = null, actions: List<ContextualizedAction> = emptyList(),
    ) {
        setup(content, reactiveValue(title), headerContent, actions)
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
            enableWhen { p -> isSceneRoot(p) }
            ifNotApplicable(Action.IfNotApplicable.Hide)
            icon(MaterialDesignR.RESIZE)
            executes { p -> p.scene.window.sizeToScene() }
        }
    }
}