package xenakis.ui.actions

import fxutils.actions.Action
import fxutils.actions.isShiftDown
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.SimpleTextPrompt
import javafx.scene.layout.Region
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.*
import xenakis.impl.Logger
import xenakis.ui.impl.NotificationView
import xenakis.ui.impl.showDialog
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.misc.HelpBrowser

object ToolWindowActions : Action.Collector<XenakisMainActivity>({
    addAction("Open console") {
        shortcut("Ctrl+T")
        icon(MaterialDesignC.CONSOLE)
        executes { screen -> screen.shellWindow.showOrBringToFront() }
    }
    addAction("Show log window") {
        shortcut("Ctrl+L")
        icon(MaterialDesignB.BELL)
        executes { screen, ev ->
            if (ev.isShiftDown()) {
                SimpleSearchableListView(Logger.Level.entries, "Select notification level").showPopup(
                    ev?.source as Region,
                    NotificationView.level
                ) { lvl ->
                    NotificationView.level = lvl
                }
            } else screen.logWindow.showOrBringToFront()
        }
    }
    addAction("Edit setup code") {
        icon(MaterialDesignF.FILE_COG)
        executes { screen, ev ->
            if (ev.isShiftDown()) screen.serverSetupCodeWindow.showOrBringToFront()
            else screen.serverTreeCodeWindow.showOrBringToFront()
        }
    }
    addAction("Open help browser") {
        shortcut("F1")
        icon(MaterialDesignW.WEB)
        executes { screen -> screen.context[HelpBrowser].show() }
    }
    addAction("Lookup documentation") {
        shortcut("Ctrl+Shift+D")
        executes { screen ->
            val searchText = SimpleTextPrompt("Look up documentation", "")
                .showDialog(screen.context) ?: return@executes
            screen.context[HelpBrowser].searchDocumentation(searchText)
        }

    }
    addAction("Show audio flows") {
        shortcut("Ctrl+F")
        icon(MaterialDesignT.TUNE)
        executes { screen -> screen.flowPaneWindow.showOrBringToFront() }
    }
    addAction("Edit settings") {
        shortcut("Ctrl+Alt+S")
        icon(MaterialDesignC.COG)
        executes { screen -> screen.settingsWindow.showOrBringToFront() }
    }
    addAction("Show samples") {
        shortcut("Ctrl+Shift+S")
        icon(Material2AL.LIBRARY_MUSIC)
        executes { screen -> screen.samplesWindow.showOrBringToFront() }
    }
    addAction("Show control buses") {
        shortcut("Ctrl+B")
        icon(Material2AL.GRAPHIC_EQ)
        executes { screen -> screen.busesWindow.showOrBringToFront() }
    }
})