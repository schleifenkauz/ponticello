package xenakis.ui.launcher

import fxutils.SubWindow
import fxutils.actions.ActionBar
import fxutils.actions.action
import fxutils.actions.registerShortcuts
import fxutils.registerShortcuts
import hextant.context.Context
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.geometry.Pos
import javafx.scene.layout.StackPane
import javafx.stage.Screen
import javafx.stage.StageStyle
import javafx.stage.Window
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import reaktive.value.binding.map
import xenakis.model.score.ScoreObject
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.score.ScoreObjectView

class DetailPaneManager(private val context: Context) {
    private val detached = mutableMapOf<ScoreObject, SubWindow>()
    private var currentlyShown: Window? = null

    fun focused(view: ScoreObjectView?) {
        currentlyShown?.hide()
        if (view == null) return
        if (view.obj in detached) {
            detached.getValue(view.obj).showOrBringToFront()
            return
        }
        val pane = StackPane(view.getDetailPane())
        val window = makeSubWindow(pane, windowTitle(view), context, SubWindow.Type.Undecorated)
        window.scene.registerShortcuts {
            on("ESCAPE") { window.hide() }
        }
        val detachAction = action<Unit>("Detach") {
            icon(MaterialDesignP.PIN_OUTLINE)
            shortcuts("Ctrl+D")
            executes { _ -> detach(view, window) }
        }.withContext(Unit)
        val commands = listOf(detachAction)
        pane.registerShortcuts(commands)
        val actionBar = ActionBar(commands, buttonStyle = "medium-icon-button")
            .floating(Pos.TOP_RIGHT)
        pane.children.add(actionBar)
        val listener = InvalidationListener { _ -> updateBounds(view, window) }
        view.layoutXProperty().addListener(listener)
        view.layoutYProperty().addListener(listener)
        window.setOnHidden {
            if (currentlyShown == window) currentlyShown = null
            view.layoutXProperty().removeListener(listener)
            view.layoutYProperty().removeListener(listener)
        }
        window.show()
        Platform.runLater {
            updateBounds(view, window)
        }
        currentlyShown = window
    }

    private fun updateBounds(view: ScoreObjectView, window: SubWindow) {
        val titleBarHeight = if (window.style == StageStyle.DECORATED) TITLE_BAR_HEIGHT else 0.0
        val boundsInScreen = view.localToScreen(view.boundsInLocal)
        val screen = Screen.getScreensForRectangle(boundsInScreen.minX, boundsInScreen.minY, 1.0, 1.0).first()
        val screenCenterY = screen.visualBounds.minY + screen.visualBounds.height / 2.0
        window.x = boundsInScreen.minX
        val prefHeight = window.scene.root.prefHeight(-1.0)
        if (boundsInScreen.centerY > screenCenterY) {
            window.height = (prefHeight + titleBarHeight).coerceAtMost(boundsInScreen.minY - screen.bounds.minY)
            window.y = boundsInScreen.minY - window.height
        } else {
            window.y = boundsInScreen.maxY
            window.height = (prefHeight + titleBarHeight).coerceAtMost(screen.bounds.maxY - window.y)
        }
    }

    private fun detach(view: ScoreObjectView, window: Window) {
        window.hide()
        val title = windowTitle(view)
        val pane = StackPane(view.getDetailPane())
        val newWindow = makeSubWindow(pane, title, context)
        val attachAction = action<Unit>("Attach") {
            icon(MaterialDesignP.PIN_OFF_OUTLINE)
            shortcuts("Ctrl+Shift+D")
            executes { _ ->
                newWindow.hide()
                detached.remove(view.obj)
                focused(view)
            }
        }.withContext(Unit)
        val actions = listOf(attachAction)
        pane.registerShortcuts(actions)
        val actionBar = ActionBar(actions, buttonStyle = "medium-icon-button")
            .floating(Pos.TOP_RIGHT)
        pane.children.add(actionBar)
        newWindow.show()
        updateBounds(view, newWindow)
        detached[view.obj] = newWindow
    }

    private fun windowTitle(view: ScoreObjectView) = view.obj.name.map { name -> "Object $name" }

    companion object {
        private const val TITLE_BAR_HEIGHT = 35.0
    }
}