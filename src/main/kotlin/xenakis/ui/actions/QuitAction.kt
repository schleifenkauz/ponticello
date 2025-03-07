package xenakis.ui.actions

import fxutils.actions.Action
import fxutils.actions.isShiftDown
import org.kordamp.ikonli.material2.Material2AL
import xenakis.ui.launcher.XenakisLauncher

object QuitAction: Action.Collector<XenakisLauncher>({
    addAction("Quit") {
        shortcut("Ctrl+Shift?+Q")
        icon(Material2AL.CLOSE)
        executes { launcher, ev ->
            launcher.closeRequest(automaticallySave = ev.isShiftDown())
        }
    }
})