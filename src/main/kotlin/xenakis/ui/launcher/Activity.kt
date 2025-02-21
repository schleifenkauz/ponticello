package xenakis.ui.launcher

import hextant.context.Context
import hextant.fx.initHextantScene
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import xenakis.ui.Icon

abstract class Activity {
    protected lateinit var stage: Stage
        private set

    abstract val context: Context

    protected abstract fun getLayout(): Parent

    protected open fun beforeShowing() {}

    protected open fun afterShowing() {}

    fun show(stage: Stage) {
        this.stage = stage
        stage.scene = Scene(getLayout())
        stage.scene.initHextantScene(context)
        stage.icons.setAll(Icon.AppIcon.image)
        beforeShowing()
        stage.show()
        afterShowing()
    }

    fun hide() {
        close()
        stage.hide()
    }

    protected open fun close() {}
}