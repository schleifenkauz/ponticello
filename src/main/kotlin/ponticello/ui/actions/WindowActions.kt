package ponticello.ui.actions

import fxutils.actions.action
import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignF
import ponticello.ui.launcher.PonticelloLauncher
import ponticello.ui.launcher.PonticelloMainActivity
import reaktive.value.binding.`if`
import reaktive.value.fx.asReactiveValue

object WindowActions {
    val quitAction = action<PonticelloLauncher>("Quit") {
        shortcut("Ctrl+Shift?+Q")
        icon(Material2AL.CLOSE)
        executes { launcher, ev ->
            launcher.quitPonticello(autoSave = ev.isShiftDown())
        }
    }

    val toggleFullScreenAction = action<PonticelloMainActivity>("Toggle Full Screen") {
        shortcut("F11")
        icon { activity ->
            `if`(
                activity.primaryStage.fullScreenProperty().asReactiveValue(),
                then = { MaterialDesignF.FULLSCREEN_EXIT }, otherwise = { MaterialDesignF.FULLSCREEN }
            )
        }
        executes { activity ->
            val window = activity.primaryStage
            window.isFullScreen = !window.isFullScreen
        }
    }

    val all = collectActions<PonticelloMainActivity> {
        add(toggleFullScreenAction)
        add(quitAction) { activity -> activity.launcher }
    }
}