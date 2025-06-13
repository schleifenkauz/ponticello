package ponticello.ui.dock

import fxutils.*
import fxutils.actions.*
import hextant.fx.initHextantScene
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.ContextMenu
import javafx.scene.input.DataFormat
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority.ALWAYS
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.robot.Robot
import javafx.stage.Popup
import javafx.stage.Stage
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.project.PonticelloProject
import ponticello.ui.dock.ToolPaneMode.*
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.impl.sceneFill
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
    open val shortcuts: Array<String> get() = type.shortcuts

    var isExclusive: Boolean = false
        private set

    private lateinit var layout: AppLayout

    val context get() = layout.context

    protected abstract val content: Parent
    protected open val headerContent: Node? get() = null
    protected open val headerActions: List<ContextualizedAction> get() = emptyList()

    lateinit var header: HBox
        private set

    var actionBar: ActionBar? = null
        private set

    protected var window: javafx.stage.Window? = null
        private set
    private var showing = reactiveVariable(false)
    var isSetup = false
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

    fun setDocked() {
        layout.showDocked(this)
    }

    fun setMode(mode: ToolPaneMode) {
        if (mode == currentMode()) return
        setShowing(false)
        this.scene?.root = Region()
        window = when (mode) {
            Docked -> null
            Window -> makeToolWindow()
            Floating -> makePopup()
        }
        if (mode != Floating) {
            setShowing(true)
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
        val ownerWindow = layout.scene.window
        val anchor = ev.popupAnchor()
        menu.show(ownerWindow, anchor.x, anchor.y)
    }

    private fun setExclusive(exclusive: Boolean) {
        isExclusive = exclusive
        if (isExclusive) {
            if (currentMode() != Docked) setMode(Docked)
            layout.setExclusive(this)
        }
    }

    open fun initialize(layout: AppLayout, state: ToolPaneState) {
        initialState = state
        this.layout = layout
        window = when (state.mode) {
            Floating -> makePopup()
            Window -> makeToolWindow()
            Docked -> null
        }
        if (state.isShowing) {
            setShowing(true)
        }
    }

    protected open fun makePopup(): Popup {
        val popup = Popup()
        popup.content.add(StackPane(this))
        popup.scene.initHextantScene(context)
        popup.sceneFill(DEFAULT_SCENE_FILL)
        popup.isAutoHide = true
        popup.setOnHidden { ev ->
            showing.set(false)
            ev.consume()
        }
        return popup
    }

    protected open fun makeToolWindow(): Stage {
        val stage = Stage()
        stage.title = title
        initialState?.windowBounds?.applyTo(stage) ?: stage.centerOnScreen()
        stage.scene = Scene(StackPane(this))
        stage.scene.initHextantScene(context)
        stage.sceneFill(DEFAULT_SCENE_FILL)
        stage.setOnHidden { ev ->
            showing.set(false)
            ev.consume()
        }
        return stage
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

    private fun currentMode() = when (window) {
        is Popup -> Floating
        is Stage -> Window
        null -> Docked
        else -> throw AssertionError("Invalid ToolPane window $window")
    }

    fun setShowing(value: Boolean) {
        if (showing.now == value) return
        if (value) {
            if (!isSetup) setup()
            when (val w = window) {
                is Popup -> showing.now = showPopup(w)
                is Stage -> {
                    w.show()
                    showing.now = true
                }

                else -> {
                    layout.showDocked(this)
                    showing.now = true
                }
            }
        } else {
            window?.hide() ?: layout.hideDocked(this)
            showing.set(false)
        }
    }

    protected open fun showPopup(popup: Popup): Boolean {
        val robot = Robot()
        popup.show(layout.scene.window, robot.mouseX, robot.mouseY)
        return true
    }

    fun toggleShowing() {
        val w = window
        if (showing.now && w != null && w is Stage) {
            if (!isSetup) setup()
            w.show()
        } else {
            setShowing(!showing.now)
        }
    }

    override fun toString(): String = "ToolPane [$title]"

    abstract class Type(val uid: Int, val title: String) : AbstractContextualObject() {
        abstract val defaultSide: Side

        abstract val icon: Ikon?

        open val shortcuts: Array<String> get() = emptyArray()

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
            executes { p -> p.setMode(Docked) }
        }
    }
}