package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.prompt.SimpleTextPrompt
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.showDialog
import ponticello.ui.misc.HelpBrowser

object ToolWindowActions : Action.Collector<AppLayout>({
    addAction("Lookup documentation") {
        shortcut("Ctrl+Shift+D")
        executes { activity ->
            val searchText = SimpleTextPrompt("Look up documentation", "")
                .showDialog(activity.context) ?: return@executes
            activity.context[HelpBrowser].searchDocumentation(searchText)
        }
    }
    addAction("Edit settings") {
        shortcut("Ctrl+Alt+S")
        icon(MaterialDesignC.COG)
        executes { activity -> activity.settingsWindow.showOrBringToFront() }
    }
})