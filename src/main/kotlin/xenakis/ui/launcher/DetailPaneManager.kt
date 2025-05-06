package xenakis.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import fxutils.SubWindow
import fxutils.actions.action
import fxutils.actions.registerShortcuts
import fxutils.createBorder
import fxutils.opacity
import fxutils.registerShortcuts
import hextant.context.Context
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Screen
import javafx.stage.StageStyle
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import reaktive.value.binding.map
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.ScoreObject
import xenakis.ui.actions.ArrowKeys
import xenakis.ui.actions.ObjectActionContext
import xenakis.ui.actions.ObjectActions
import xenakis.ui.impl.DEFAULT_SCENE_FILL
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.impl.sceneFill
import xenakis.ui.midi.ContextualMidiReceiver
import xenakis.ui.midi.ParameterControlsMidiContext
import xenakis.ui.score.ScoreObjectView

class DetailPaneManager(private val context: Context) {
    private val detached = mutableMapOf<ScoreObject, SubWindow>()
    private var currentlyShown: SubWindow? = null
    private var attachedToView: ScoreObjectView? = null

    fun showDetailPane(view: ScoreObjectView?) {
        currentlyShown?.hide()
        if (view == null) {
            currentlyShown = null
            attachedToView = null
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

    fun hideCurrentlyShown() {
        currentlyShown?.hide()
        currentlyShown = null
        attachedToView = null
    }

    private fun showDetailPaneFor(view: ScoreObjectView) {
        lateinit var window: SubWindow
        val detachAction = action<Unit>("Detach") {
            icon(MaterialDesignP.PIN_OUTLINE)
            shortcuts("Ctrl+D")
            executes { _ ->
                window.hide()
                detach(view)
            }
        }.withContext(Unit)
        val detailPane = view.getDetailPane(listOf(detachAction))
        val pane = StackPane(detailPane)
        pane.border = createBorder(Color.GRAY, 1.0)
        window = makeSubWindow(pane, windowTitle(view), context, SubWindow.Type.Undecorated)
            .sceneFill(DEFAULT_SCENE_FILL.opacity(0.5))
        registerMidiContext(view, window)
        window.scene.registerShortcuts {
            on("ESCAPE") { window.hide() }
        }
        ArrowKeys.registerArrowKeys(window.scene, context)
        addActions(view, pane)
        updateBoundsOnMove(view, window)
        window.show()
        Platform.runLater {
            updateBounds(view, window)
        }
        currentlyShown = window
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

    companion object : PublicProperty<DetailPaneManager> by publicProperty("DetailPaneManager") {
        private const val TITLE_BAR_HEIGHT = 35.0
    }
}