package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.actions.isShiftDown
import org.kordamp.ikonli.material2.Material2AL
import ponticello.ui.launcher.PonticelloLauncher

object QuitAction: Action.Collector<PonticelloLauncher>({
    addAction("Quit") {
        shortcut("Ctrl+Shift?+Q")
        icon(Material2AL.CLOSE)
        executes { launcher, ev ->
            launcher.closeRequest(automaticallySave = ev.isShiftDown())
        }
    }
})