package ponticello.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.*
import fxutils.actions.action
import fxutils.actions.registerShortcuts
import hextant.context.Context
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.scene.Node
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Screen
import javafx.stage.StageStyle
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ScoreObject
import ponticello.ui.actions.ArrowKeys
import ponticello.ui.actions.ObjectActionContext
import ponticello.ui.actions.ObjectActions
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.midi.ContextualMidiReceiver
import ponticello.ui.midi.ParameterControlsMidiContext
import ponticello.ui.score.ScoreObjectSelectionManager
import ponticello.ui.score.ScoreObjectView
import reaktive.Observer
import reaktive.value.binding.map

class ScoreObjectDetailPane(private val context: Context): ToolPane() {
    private val detached = mutableMapOf<ScoreObject, SubWindow>()
    private var currentlyShown: SubWindow? = null
    private var attachedToView: ScoreObjectView? = null
    private val focusedViewObserver: Observer

    override val title: String
        get() = "Score Object Details"

    override val icon: Ikon
        get() = MaterialDesignT.TUNE_VARIANT

    override var content: Node = noSelectedObject()
        set(value) {
            children.replace(field, value)
            field = value
        }

    init {
        setup()
        val selector = context[ScoreObjectSelectionManager]
        focusedViewObserver = selector.focusedView.observe { _, _, focused ->
            if (window == null || window!!.type == SubWindow.Type.ToolWindow) {
                showDetailPane(focused)
            }
        }
        context[ScoreObjectDetailPane] = this
    }

    override fun defaultState(): ToolPaneState = ToolPaneState.docked(ToolPaneState.Side.LEFT)

    private fun noSelectedObject() = BorderPane(label("No object selected"))

    fun showDetailPane(view: ScoreObjectView?) {
        currentlyShown?.hide()
        if (view == null) {
            currentlyShown = null
            attachedToView = null
            content = noSelectedObject()
            return
        }
        when {
            attachedToView != null && currentlyShown != null && attachedToView != view -> detach(view)
            view.obj in detached -> reattach(detached.getValue(view.obj), view)
            else -> {
                showDetailPaneFor(view)
                attachedToView = view
            }
        }
    }

    fun hideDetailPane(view: ScoreObjectView) {
        if (attachedToView == view) hideCurrentlyShown()
    }

    fun hideCurrentlyShown() {
        currentlyShown?.hide()
        currentlyShown = null
        attachedToView = null
        if (window != null && window!!.type != SubWindow.Type.ToolWindow) {
            setShowing(false)
        }
    }

    private fun showDetailPaneFor(view: ScoreObjectView) {
        val detachAction = action<Unit>("Detach") {
            icon(MaterialDesignP.PIN_OUTLINE)
            shortcuts("Ctrl+D")
            executes { _ ->
                window?.hide()
                detach(view)
            }
        }.withContext(Unit)
        val detailPane = view.getDetailPane(listOf(detachAction))
        val pane = StackPane(detailPane)
        window?.let { w ->
            pane.border = createBorder(Color.GRAY, 1.0)
            w.sizeToScene()
            registerMidiContext(view, w)
            w.scene.registerShortcuts {
                on("ESCAPE") { w.hide() }
            }
            ArrowKeys.registerArrowKeys(w.scene, context)
            updateBoundsOnMove(view, w)
            Platform.runLater {
                updateBounds(view, w)
            }
        }
        addActions(view, pane)
        content = pane
        currentlyShown = window
        setShowing(true)
    }

    private fun updateBoundsOnMove(view: ScoreObjectView, window: SubWindow) {
        val listener = InvalidationListener { _ -> updateBounds(view, window) }
        view.layoutXProperty().addListener(listener)
        view.layoutYProperty().addListener(listener)
        window.setOnHidden {
            if (currentlyShown == window) currentlyShown = null
            view.layoutXProperty().removeListener(listener)
            view.layoutYProperty().removeListener(listener)
        }
    }

    private fun updateBounds(view: ScoreObjectView, window: SubWindow) {
        val titleBarHeight = if (window.style == StageStyle.DECORATED) TITLE_BAR_HEIGHT else 0.0
        val boundsInScreen = view.localToScreen(view.boundsInLocal) ?: return
        val screen = Screen.getScreensForRectangle(boundsInScreen.centerX, boundsInScreen.centerY, 1.0, 1.0)
            .firstOrNull() ?: return
        val screenCenterY = screen.visualBounds.minY + screen.visualBounds.height / 2.0
        window.x = boundsInScreen.minX.coerceIn(screen.visualBounds.minX, screen.visualBounds.maxX - window.width)
        val prefHeight = window.scene.root.prefHeight(-1.0)
        if (boundsInScreen.centerY > screenCenterY) {
            window.height = (prefHeight + titleBarHeight).coerceAtMost(boundsInScreen.minY - screen.bounds.minY)
            window.y = boundsInScreen.minY - window.height
        } else {
            window.y = boundsInScreen.maxY
            window.height = (prefHeight + titleBarHeight).coerceAtMost(screen.bounds.maxY - window.y)
        }
    }

    private fun detach(view: ScoreObjectView) {
        val title = windowTitle(view)
        lateinit var newWindow: SubWindow
        val attachAction = action<Unit>("Attach") {
            icon(MaterialDesignP.PIN_OFF_OUTLINE)
            shortcuts("Ctrl+Shift+D")
            executes { _ ->
                reattach(newWindow, view)
            }
        }.withContext(Unit)
        val detailPane = view.getDetailPane(extraActions = listOf(attachAction))
        val pane = StackPane(detailPane)
        newWindow = makeSubWindow(pane, title, context)
        registerMidiContext(view, newWindow)
        addActions(view, pane)
        newWindow.show()
        updateBounds(view, newWindow)
        detached[view.obj] = newWindow
    }

    private fun registerMidiContext(view: ScoreObjectView, newWindow: SubWindow) {
        val obj = view.obj
        if (obj is ParameterizedObject) {
            context[ContextualMidiReceiver].registerMidiContext(newWindow) { ParameterControlsMidiContext(obj.controls) }
        }
    }

    private fun reattach(newWindow: SubWindow, view: ScoreObjectView) {
        newWindow.hide()
        detached.remove(view.obj)
        showDetailPaneFor(view)
    }

    private fun addActions(view: ScoreObjectView, pane: StackPane) {
        val ctx = ObjectActionContext.SingleObjectContext(view)
        val actions = ObjectActions.singleObjectActions.withContext(ctx) +
                ObjectActions.multiObjectActions.withContext(ctx)
        pane.registerShortcuts(actions)
    }

    private fun windowTitle(view: ScoreObjectView) = view.obj.name.map { name -> "Object $name" }

    companion object : PublicProperty<ScoreObjectDetailPane> by publicProperty("DetailPaneManager") {
        private const val TITLE_BAR_HEIGHT = 35.0
    }
}