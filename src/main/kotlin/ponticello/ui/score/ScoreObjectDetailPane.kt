package ponticello.ui.score

import fxutils.*
import fxutils.actions.Action
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import javafx.scene.Parent
import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.stage.Popup
import javafx.stage.Screen
import javafx.stage.Window
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import ponticello.model.project.PonticelloProject
import ponticello.model.score.ScoreObject
import ponticello.ui.actions.ArrowKeys
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.impl.sceneFill
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.binding.impl.notNull
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable

class ScoreObjectDetailPane : ToolPane() {
    private val detached = mutableMapOf<ScoreObject, SubWindow>()
    private val displayedObject: ReactiveVariable<ScoreObjectView?> = reactiveVariable(null)
    private lateinit var focusedViewObserver: Observer

    override val type: Type
        get() = ScoreObjectDetailPane
    override var content: Parent = noSelectedObject()
        set(value) {
            children.replace(field, value)
            field = value
            setVgrow(value, Priority.ALWAYS)
        }

    override fun doSetup() {
        val selector = context[ScoreObjectSelectionManager]
        focusedViewObserver = selector.focusedView.observe { _, _, focused ->
            if (window !is Popup) {
                viewDetails(focused)
            }
        }
    }

    override val headerActions: List<ContextualizedAction>
        get() = actions.withContext(this) + super.headerActions

    override fun defaultState(): ToolPaneState = ToolPaneState.docked

    private fun noSelectedObject() = BorderPane(label("No object focused"))

    fun viewDetails(focusedView: ScoreObjectView?) {
        if (focusedView != null && focusedView.obj in detached) {
            detached.getValue(focusedView.obj).showAndBringToFront()
            return
        }
        displayedObject.now = focusedView
        if (focusedView == null) {
            content = noSelectedObject()
            if (window is Popup) {
                setShowing(false)
            }
            return
        }
        val pane = ScrollPane(focusedView.detailPane)
        pane.isFitToWidth = true
        content = pane
    }

    fun hidePopup() {
        if (window is Popup) {
            setShowing(false)
        }
    }

    override fun makePopup(): Popup {
        val popup = super.makePopup()
        popup.isAutoFix = true
        popup.sizeToScene()
        popup.sceneFill(DEFAULT_SCENE_FILL.opacity(0.5))
        ArrowKeys.registerArrowKeys(popup.scene, context)
        return popup
    }

    override fun showPopup(popup: Popup, ownerWindow: Window): Boolean {
        val view = displayedObject.now ?: return false
        val titleBarHeight = 0.0
        val boundsInScreen = view.localToScreen(view.boundsInLocal) ?: return false
        val screen = Screen.getScreensForRectangle(boundsInScreen.centerX, boundsInScreen.centerY, 1.0, 1.0)
            .firstOrNull() ?: return false
        val screenCenterY = screen.visualBounds.minY + screen.visualBounds.height / 2.0
        val x = boundsInScreen.minX.coerceIn(screen.visualBounds.minX, screen.visualBounds.maxX)
        val y: Double
        val prefHeight = popup.scene.root.prefHeight(-1.0)
        if (boundsInScreen.centerY > screenCenterY) {
            popup.height = (prefHeight + titleBarHeight).coerceAtMost(boundsInScreen.minY - screen.bounds.minY)
            y = boundsInScreen.minY - popup.height
        } else {
            y = boundsInScreen.maxY
            popup.height = (prefHeight + titleBarHeight).coerceAtMost(screen.bounds.maxY - y)
        }
        popup.show(view, x, y)
        return true
    }

    private fun detach() {
        val view = displayedObject.now ?: return
        viewDetails(null)
        val title = windowTitle(view)
        lateinit var newWindow: SubWindow
        val pane = StackPane(view.detailPane)
        newWindow = makeSubWindow(pane, title, context)
        newWindow.show()
        detached[view.obj] = newWindow
        newWindow.setOnHidden {
            detached.remove(view.obj)
        }
    }

    private fun windowTitle(view: ScoreObjectView) = view.obj.name.map { name -> "Object $name" }

    companion object : Type(uid = 5, "Score Object Details") {
        private val actions = collectActions<ScoreObjectDetailPane> {
            addAction("Detach") {
                icon(MaterialDesignP.PIN_OUTLINE)
                shortcuts("Ctrl+D")
                enableWhen { pane -> pane.displayedObject.notNull() }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                executes { pane -> pane.detach() }
            }
        }

        override val icon: Ikon
            get() = MaterialDesignT.TUNE_VARIANT

        override val shortcut: String
            get() = "F8"

        override val defaultSide: Side
            get() = Side.LEFT

        override fun createToolPane(project: PonticelloProject): ToolPane = ScoreObjectDetailPane()
    }
}