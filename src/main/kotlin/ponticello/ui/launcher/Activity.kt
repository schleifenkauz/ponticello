package ponticello.ui.launcher

import fxutils.runAfterLayout
import hextant.context.Context
import hextant.fx.initHextantScene
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage
import javafx.stage.Window

abstract class Activity {
    lateinit var primaryStage: Stage
        private set

    abstract val context: Context

    protected abstract fun getLayout(): Parent

    protected open fun beforeShowing(): Unit = Unit

    protected open fun afterShowing() {
        setVisible()
    }

    protected fun setVisible() {
        primaryStage.scene.root.isVisible = true
    }

    fun show(stage: Stage) {
        this.primaryStage = stage
        stage.scene = Scene(getLayout())
        stage.scene.root.isVisible = false
        stage.scene.initHextantScene(context)
        stage.icons.setAll(APP_ICON)
        beforeShowing()
        stage.show()
        runAfterLayout {
            afterShowing()
        }
    }

    fun hide() {
        close()
        for (window in Window.getWindows().toList()) {
            window.hide()
        }
    }

    protected open fun close() {}

    companion object {
        val APP_ICON =
            Image(PonticelloApp::class.java.getResource("/ponticello/ui/icons/appicon.png")!!.toExternalForm())
    }
}