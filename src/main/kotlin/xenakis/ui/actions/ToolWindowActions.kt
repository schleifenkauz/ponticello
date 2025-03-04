package xenakis.ui.actions

import javafx.scene.Node
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.*
import xenakis.model.Logger
import xenakis.ui.impl.NotificationView
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.misc.HelpBrowser
import xenakis.ui.prompt.SimpleTextPrompt
import xenakis.ui.registry.SimpleSearchableListView

object ToolWindowActions : Action.Collector<XenakisMainActivity>({
    addAction("Open console") {
        shortcut("Ctrl+T")
        icon(MaterialDesignC.CONSOLE)
        executes { screen -> screen.shellWindow.show() }
    }
    addAction("Show log window") {
        shortcut("Ctrl+L")
        icon(MaterialDesignB.BELL)
        executes { screen, ev ->
            if (ev.isShiftDown()) {
                SimpleSearchableListView(Logger.Level.entries, "Select notification level").showPopup(
                    screen.context,
                    ev?.source as Node,
                    NotificationView.level
                ) { lvl ->
                    NotificationView.level = lvl
                }
            } else screen.logWindow.show()
        }
    }
    addAction("Edit setup code") {
        icon(MaterialDesignF.FILE_COG)
        executes { screen, ev ->
            if (ev.isShiftDown()) screen.serverSetupCodeWindow.show()
            else screen.serverTreeCodeWindow.show()
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
    addAction("Edit audio flow graph") {
        shortcut("Ctrl+Shift+F")
        icon(MaterialDesignG.GRAPH)
        executes { screen -> screen.flowGraphWindow.show() }
    }
    addAction("Show audio flows") {
        shortcut("Ctrl+Alt+F")
        icon(MaterialDesignT.TUNE)
        executes { screen -> screen.flowPaneWindow.show() }
    }
    addAction("Edit settings") {
        shortcut("Ctrl+Alt+S")
        icon(MaterialDesignC.COG)
        executes { screen -> screen.settingsWindow.show() }
    }
    addAction("Show samples") {
        shortcut("Ctrl+F")
        icon(Material2AL.LIBRARY_MUSIC)
        executes { screen -> screen.samplesWindow.show() }
    }
    addAction("Show global controls") {
        shortcut("Ctrl+Shift+G")
        icon(Material2AL.GRAIN)
        executes { screen -> screen.globalControlsWindow.show() }
    }
    addAction("Show groups") {
        shortcut("Ctrl+G")
        icon(Material2AL.IMPORT_EXPORT)
        executes { screen -> screen.groupsWindow.show() }
    }
    addAction("Show buses") {
        shortcut("Ctrl+B")
        icon(Material2AL.GRAPHIC_EQ)
        executes { screen -> screen.busesWindow.show() }
    }
})