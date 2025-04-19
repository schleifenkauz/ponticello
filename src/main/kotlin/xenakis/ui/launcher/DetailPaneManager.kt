package xenakis.ui.launcher

import fxutils.SubWindow
import fxutils.actions.ActionBar
import fxutils.actions.action
import fxutils.actions.registerShortcuts
import fxutils.infiniteSpace
import hextant.context.Context
import javafx.beans.Observable
import javafx.scene.layout.HBox
import javafx.stage.Window
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import reaktive.value.binding.map
import xenakis.model.score.ScoreObject
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.score.ScoreObjectView

class DetailPaneManager(private val context: Context) {
    private val detached = mutableMapOf<ScoreObject, Window>()
    private var currentlyShown: Window? = null

    fun focused(view: ScoreObjectView?) {
        currentlyShown?.hide()
        if (view == null) return
        if (view.obj in detached) return
        val pane = view.getDetailPane()
        val window = makeSubWindow(pane, windowTitle(view), context, SubWindow.Type.Undecorated)
        val detachAction = action<Unit>("Detach") {
            icon(MaterialDesignP.PIN)
            shortcuts("Ctrl+D")
            executes { _ -> detach(view, window) }
        }.withContext(Unit)
        val actionBar = ActionBar(listOf(detachAction), buttonStyle = "medium-icon-button")
        pane.children.add(0, HBox(infiniteSpace(), actionBar))
        pane.registerShortcuts(listOf(detachAction))
        val listener: (Observable) -> Unit = { _ -> updateBounds(view, window) }
        view.boundsInLocalProperty().addListener(listener)
        window.setOnHidden { view.boundsInLocalProperty().removeListener(listener) }
        updateBounds(view, window)
        window.show()
        window.sizeToScene()
        currentlyShown = window
    }

    private fun updateBounds(view: ScoreObjectView, window: Window) {
        val screenBounds = view.localToScreen(view.boundsInLocal)
        window.x = screenBounds.minX
        window.y = screenBounds.maxY
    }

    private fun detach(view: ScoreObjectView, window: Window) {
        if (window == currentlyShown) {
            currentlyShown = null
        }
        window.hide()
        val title = windowTitle(view)
        val newWindow = makeSubWindow(view.getDetailPane(), title, context)
        newWindow.show()
        newWindow.sizeToScene()
        detached[view.obj] = newWindow
    }

    private fun windowTitle(view: ScoreObjectView) = view.obj.name.map { name -> "Object $name" }
}