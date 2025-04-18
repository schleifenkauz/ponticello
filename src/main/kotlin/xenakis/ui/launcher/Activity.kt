package xenakis.ui.launcher

import hextant.context.Context
import hextant.fx.initHextantScene
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage

abstract class Activity {
    lateinit var primaryStage: Stage
        private set

    abstract val context: Context

    protected abstract fun getLayout(): Parent

    protected open fun beforeShowing() {}

    protected open fun afterShowing() {}

    fun show(stage: Stage) {
        this.primaryStage = stage
        stage.scene = Scene(getLayout())
        stage.scene.initHextantScene(context)
        stage.icons.setAll(APP_ICON)
        beforeShowing()
        stage.show()
        afterShowing()
    }

    fun hide() {
        close()
        primaryStage.hide()
    }

    protected open fun close() {}

    companion object {
        val APP_ICON = Image(XenakisApp::class.java.getResource("/xenakis/ui/icons/appicon.png")!!.toExternalForm())
    }
}