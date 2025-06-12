package ponticello.ui.dock

import fxutils.*
import fxutils.actions.*
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.control.ContextMenu
import javafx.scene.input.DataFormat
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority.ALWAYS
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.project.PonticelloProject
import ponticello.ui.dock.ToolPaneMode.*
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.impl.sceneFill
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import reaktive.value.ReactiveBoolean
import reaktive.value.binding.Binding
import reaktive.value.binding.equalTo
import reaktive.value.binding.flatMap
import reaktive.value.fx.asReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable

abstract class ToolPane : VBox() {
    var initialState: ToolPaneState? = null
        private set

    open val type: Type get() = throw UnsupportedOperationException("$this has no type")
    open val title get() = type.title
    open val icon get() = type.icon
    open val shortcuts: Array<String> get() = emptyArray()

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
    protected var isSetup = false
        private set

    val isShowing: ReactiveBoolean get() = showing

    init {
        styleClass("tool-pane")
    }

    protected open fun doSetup() {

    }

    protected open fun afterSetup() {

    }

    fun setup() {
        if (isSetup) return
        doSetup()
        header = createHeader()
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

    private fun createHeader(): HBox {
        actionBar = ActionBar(headerActions, buttonStyle = "medium-icon-button")
        val label = label(title).styleClass("heading")
        val box = HBox(label, infiniteSpace(), actionBar).styleClass("tool-pane-header")
        if (headerContent != null) box.children.add(1, headerContent)
        return box
    }

    open fun handleShortcut(ev: KeyEvent) {
        if (shortcuts.isNotEmpty() && shortcuts.first().shortcut.matches(ev)) {
            toggleShowing()
        }
    }

    fun setUndocked(windowType: SubWindow.Type): SubWindow {
        if (isShowing.now) {
            window?.let { w ->
                w.close()
                w.scene.root = Region()
            } ?: layout.hideDocked(this)
        }
        window = SubWindow(this, title, windowType).sceneFill(DEFAULT_SCENE_FILL)
        window!!.sizeToScene()
        window!!.setOnHidden {
            showing.set(false)
        }
        setShowing(true)
        return window!!
    }

    fun setDocked() {
        window?.close()
        window?.scene?.root = Region()
        window = null
        showing.now = true
        layout.showDocked(this)
    }

    fun setMode(mode: ToolPaneMode) {
        if (mode == currentMode()) return
        when (mode) {
            Docked -> setDocked()
            Window -> setUndocked(SubWindow.Type.ToolWindow)
            Floating -> setUndocked(SubWindow.Type.Popup)
        }
    }

    fun showToolPaneConfigMenu(ev: Event?) {
        val menu = ContextMenu()
        for (mode in ToolPaneMode.entries) {
            val icon = FontIcon(Material2AL.CHECK) styleClass "check-icon"
            icon.isVisible = currentMode() == mode
            menu.items.add(menuItem(mode.name, icon) {
                setMode(mode)
            })
        }
        val exclusiveCheck = FontIcon(Material2AL.CHECK) styleClass "check-icon"
        exclusiveCheck.isVisible = isExclusive
        val exclusiveItem = menuItem("Exclusive", exclusiveCheck) {
            setExclusive(!isExclusive)
        }
        menu.items.add(exclusiveItem)
        val ownerWindow = layout.context[primaryStage]
        val anchor = ev.popupAnchor()
        menu.show(ownerWindow, anchor.x, anchor.y)
    }

    private fun setExclusive(exclusive: Boolean) {
        isExclusive = exclusive
        if (isExclusive) {
            if (currentMode() != Docked) setDocked()
            layout.setExclusive(this)
        }
    }

    open fun initialize(layout: AppLayout, state: ToolPaneState) {
        initialState = state
        this.layout = layout
        if (state.mode == Window || state.mode == Floating) {
            val windowType =
                if (state.mode == Floating) SubWindow.Type.Popup
                else SubWindow.Type.ToolWindow
            val bounds = state.windowBounds
            window = SubWindow(this, title, windowType).also { w ->
                if (bounds != null) {
                    w.relocate(bounds.x, bounds.y)
                    w.width = bounds.width
                    w.height = bounds.height
                } else {
                    w.centerOnScreen()
                    w.sizeToScene()
                }
            }.sceneFill(DEFAULT_SCENE_FILL)
        }
        if (state.isShowing) {
            setShowing(true)
        }
    }

    open fun defaultState(): ToolPaneState =
        throw UnsupportedOperationException("Default state is not supported for $this")

    open fun saveState(dest: ToolPaneState) {
        dest.uid = type.uid
        window?.let { w ->
            if (!w.x.isNaN() && !w.y.isNaN() && !w.width.isNaN() && !w.height.isNaN()) {
                dest.windowBounds = WindowBounds(w.x, w.y, w.width, w.height)
            }
        }
        dest.mode = currentMode()
        dest.isShowing = isShowing.now
        dest.isExclusive = isExclusive
    }

    private fun currentMode() = when {
        window == null -> Docked
        window!!.type == SubWindow.Type.Popup -> Floating
        else -> Window
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
            if (!isSetup) setup()
            window!!.showOrBringToFront()
        } else {
            setShowing(!showing.now)
        }
    }

    override fun toString(): String = "ToolPane [$title]"

    abstract class Type(val uid: Int, val title: String): AbstractContextualObject() {
        abstract val defaultSide: Side

        abstract val icon: Ikon?

        abstract fun createToolPane(project: PonticelloProject): ToolPane

        override fun toString(): String = "ToolPane [$title]"

        companion object {
            val DATA_FORMAT = DataFormat("ponticello/tool-pane-type")
        }
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

        val redockAction = action<ToolPane>("Dock") {
            enableWhen { p -> isSceneRoot(p) }
            ifNotApplicable(Action.IfNotApplicable.Hide)
            //TODO find icon
            executes { p -> p.setDocked() }
        }
    }
}