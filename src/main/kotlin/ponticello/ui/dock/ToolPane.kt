package ponticello.ui.dock

import fxutils.*
import fxutils.actions.*
import hextant.fx.initHextantScene
import javafx.event.Event
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.input.DataFormat
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority.ALWAYS
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.robot.Robot
import javafx.stage.Modality
import javafx.stage.Popup
import javafx.stage.Stage
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import org.kordamp.ikonli.materialdesign2.MaterialDesignW
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.project.PonticelloProject
import ponticello.ui.dock.Side.*
import ponticello.ui.dock.ToolPaneMode.*
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.impl.sceneFill
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import reaktive.value.ReactiveBoolean
import reaktive.value.binding.*
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
    protected open val headerActions: List<ContextualizedAction>
        get() = actions.withContext(this)

    lateinit var header: HBox
        private set

    lateinit var heading: Label
        private set

    lateinit var actionBar: ActionBar
        private set

    protected var window: javafx.stage.Window? = null
        private set
    private var showing = reactiveVariable(false)
    var isSetup = false
        private set

    val isShowing: ReactiveBoolean get() = showing

    val side by lazy { reactiveVariable(type.defaultSide) }

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
        relayout()
        registerShortcuts(headerActions)
        afterSetup()
        isSetup = true
    }

    fun relayout() {
        children.clear()
        header = createHeader()
        children.add(header)
        val content = content
        children.add(content)
        setVgrow(content, ALWAYS)
    }

    override fun requestFocus() {
        headerContent?.requestFocus() ?: content.requestFocus()
    }

    private fun createHeader(): HBox {
        actionBar = ActionBar(headerActions, buttonStyle = "medium-icon-button")
        heading = label(title).styleClass("heading")
        val space = infiniteSpace()
        val box = HBox(heading, space, actionBar).styleClass("tool-pane-header")
        if (headerContent != null) box.children.add(1, headerContent)
        heading.setupWindowDragging(Cursor.DEFAULT) { scene.window as? Popup }
        space.setupWindowDragging(Cursor.DEFAULT) { scene.window as? Popup }
        return box
    }

    open fun handleShortcut(ev: KeyEvent) {
        if (shortcuts.isNotEmpty() && shortcuts.first().shortcut.matches(ev)) {
            toggleShowing()
        }
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
        val root = StackPane(this)
        root.border = solidBorder(Color.BLACK)
        popup.content.add(root)
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
        stage.initOwner(context[primaryStage])
        stage.initModality(Modality.NONE)
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

    fun setShowing(value: Boolean, ownerWindow: javafx.stage.Window = layout.context[primaryStage]) {
        if (showing.now == value) return
        if (value) {
            if (!isSetup) setup()
            when (val w = window) {
                is Popup -> showing.now = showPopup(w, ownerWindow)
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

    protected open fun showPopup(popup: Popup, ownerWindow: javafx.stage.Window): Boolean {
        val robot = Robot()
        popup.show(ownerWindow, robot.mouseX, robot.mouseY)
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

        open val icon: Ikon? get() = null

        open val shortcuts: Array<String> get() = emptyArray()

        abstract fun createToolPane(project: PonticelloProject): ToolPane

        override fun toString(): String = "ToolPane [$title]"

        companion object {
            val DATA_FORMAT = DataFormat("ponticello/tool-pane-type")
        }
    }

    companion object {
        private fun isSceneRoot(node: Node): ReactiveBoolean {
            val parent = node.parentProperty().asReactiveValue()
            return parent.map { p -> p is StackPane } and
                    node.sceneProperty().asReactiveValue().flatMap { s ->
                        s?.rootProperty()?.asReactiveValue()?.equalTo(parent) ?: reactiveValue(false)
                    }
        }

        private val actions = collectActions<ToolPane> {
            addAction("Resize window to fit contents") {
                shortcut("Ctrl+L")
                enableWhen { p -> isSceneRoot(p) }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                icon(MaterialDesignR.RESIZE)
                executes { p -> p.scene.window.sizeToScene() }
            }
            addAction("Dock") {
                enableWhen { p -> isSceneRoot(p) and p.side.notEqualTo(TOP) }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                icon { p ->
                    p.side.map { side ->
                        when (side) {
                            LEFT -> MaterialDesignP.PAGE_LAYOUT_SIDEBAR_LEFT
                            RIGHT -> MaterialDesignP.PAGE_LAYOUT_SIDEBAR_RIGHT
                            BOTTOM -> MaterialDesignP.PAGE_LAYOUT_FOOTER
                            TOP -> MaterialDesignP.PROGRESS_QUESTION //cannot happen
                        }
                    }
                }
                executes { p -> p.setMode(Docked) }
            }
            addAction("Hide") {
                icon(MaterialDesignW.WINDOW_MINIMIZE)
                enableWhen { p -> isSceneRoot(p).not() }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                executes { p -> p.setShowing(false) }
            }
        }
    }
}