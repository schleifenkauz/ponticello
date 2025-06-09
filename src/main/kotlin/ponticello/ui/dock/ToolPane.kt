package ponticello.ui.dock

import fxutils.*
import fxutils.actions.*
import javafx.scene.Node
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority.ALWAYS
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.ReactiveBoolean
import reaktive.value.binding.Binding
import reaktive.value.binding.equalTo
import reaktive.value.binding.flatMap
import reaktive.value.fx.asReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable

abstract class ToolPane : VBox() {
    abstract val title: String
    open val icon: Ikon? get() = null
    open val shortcuts: Array<String> get() = emptyArray()

    open val hasSecondaryFunction: Boolean get() = false

    lateinit var side: ToolPaneState.Side
        private set
    var isExclusive: Boolean = false
        private set

    private lateinit var layout: AppLayout

    protected abstract val content: Node
    protected open val headerContent: Node? get() = null
    protected open val headerActions: List<ContextualizedAction> get() = emptyList()

    lateinit var header: HBox
        private set

    var actionBar: ActionBar? = null
        private set

    protected var window: SubWindow? = null
        private set
    private var showing = reactiveVariable(false)
    private var isSetup = false

    val isShowing: ReactiveBoolean get() = showing

    init {
        styleClass("tool-pane")
    }

    protected open fun doSetup() {

    }

    protected open fun afterSetup() {

    }

    private fun setup() {
        doSetup()
        header = createHeader(headerActions)
        children.add(header)
        children.add(content)
        setVgrow(content, ALWAYS)
        registerShortcuts(headerActions)
        afterSetup()
        isSetup = true
    }

    override fun requestFocus() {
        headerContent?.requestFocus() ?: content.requestFocus()
    }

    private fun createHeader(headerActions: List<ContextualizedAction>): HBox {
        actionBar = ActionBar(headerActions, buttonStyle = "medium-icon-button")
        val label = label(title).styleClass("heading")
        val box = HBox(label, infiniteSpace(), actionBar).styleClass("tool-pane-header")
        if (headerContent != null) box.children.add(0, headerContent)
        return box
    }

    open fun handleShortcut(ev: KeyEvent) {
        if (shortcuts.isNotEmpty() && shortcuts.first().shortcut.matches(ev)) {
            toggleShowing()
        }
    }

    fun setUndocked(windowType: SubWindow.Type): SubWindow {
        window?.let { w ->
            w.close()
            w.scene.root = Region()
        } ?: layout.hideDocked(this)
        window = SubWindow(this, title, windowType)
        setShowing(true)
        return window!!
    }

    fun setDocked() {
        window?.close()
        window?.scene?.root = Region()
        window = null
        layout.showDocked(this)
    }

    open fun defaultState(): ToolPaneState =
        throw UnsupportedOperationException("Default state is not supported for $title")

    fun initialize(layout: AppLayout, state: ToolPaneState) {
        this.layout = layout
        side = state.side
        if (state.position is ToolPanePosition.Undocked) {
            window = SubWindow(this, title, state.position.windowType).also { w ->
                w.relocate(state.position.x, state.position.y)
                w.width = state.position.width
                w.height = state.position.height
            }
        }
        if (state.isShowing) {
            setShowing(true)
        }
    }

    fun currentState(): ToolPaneState {
        val window = window
        val position =
            if (window != null) {
                ToolPanePosition.Undocked(
                    window.type,
                    window.x, window.y,
                    window.width, window.height
                )
            } else ToolPanePosition.Docked
        return ToolPaneState(side, position, isShowing.now, isExclusive)
    }

    fun setShowing(value: Boolean) {
        if (showing.now == value) return
        showing.set(value)
        if (value) {
            if (!isSetup) setup()
            window?.showOrBringToFront() ?: layout.showDocked(this)
        } else {
            window?.hide() ?: layout.hideDocked(this)
        }
    }

    fun toggleShowing() {
        if (showing.now && window != null && window!!.type == SubWindow.Type.ToolWindow) {
            window!!.showOrBringToFront()
        } else {
            setShowing(!showing.now)
        }
    }

    override fun toString(): String = "ToolPane [$title]"

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