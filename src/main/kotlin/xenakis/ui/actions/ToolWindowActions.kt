package xenakis.ui.actions

import fxutils.actions.Action
import fxutils.actions.isShiftDown
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.SimpleTextPrompt
import javafx.scene.layout.Region
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.*
import xenakis.impl.Logger
import xenakis.model.registry.BusRegistry
import xenakis.ui.impl.NotificationView
import xenakis.ui.impl.showDialog
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.midi.ContextualMidiReceiver
import xenakis.ui.midi.ControlBusesMidiReceiver
import xenakis.ui.misc.HelpBrowser

object ToolWindowActions : Action.Collector<XenakisMainActivity>({
    addAction("Open console") {
        shortcut("Ctrl+T")
        icon(MaterialDesignC.CONSOLE)
        executes { activity -> activity.shellWindow.showOrBringToFront() }
    }
    addAction("Show log window") {
        shortcut("Ctrl+L")
        icon(MaterialDesignB.BELL)
        executes { activity, ev ->
            if (ev.isShiftDown()) {
                val lvl = SimpleSearchableListView(Logger.Level.entries, "Select notification level").showPopup(
                    ev?.source as Region,
                    NotificationView.level
                )
                if (lvl != null) NotificationView.level = lvl
            } else activity.logWindow.showOrBringToFront()
        }
    }
    addAction("Edit setup code") {
        icon(MaterialDesignF.FILE_COG)
        executes { activity, ev ->
            if (ev.isShiftDown()) activity.serverSetupCodeWindow.showOrBringToFront()
            else activity.serverTreeCodeWindow.showOrBringToFront()
        }
    }
    addAction("Open help browser") {
        shortcut("F1")
        icon(MaterialDesignW.WEB)
        executes { activity -> activity.context[HelpBrowser].show() }
    }
    addAction("Lookup documentation") {
        shortcut("Ctrl+Shift+D")
        executes { activity ->
            val searchText = SimpleTextPrompt("Look up documentation", "")
                .showDialog(activity.context) ?: return@executes
            activity.context[HelpBrowser].searchDocumentation(searchText)
        }

    }
    addAction("Show audio flows") {
        shortcut("Ctrl+F")
        icon(MaterialDesignT.TUNE)
        executes { activity -> activity.flowPaneWindow.showOrBringToFront() }
    }
    addAction("Edit settings") {
        shortcut("Ctrl+Alt+S")
        icon(MaterialDesignC.COG)
        executes { activity -> activity.settingsWindow.showOrBringToFront() }
    }
    addAction("Show samples") {
        shortcut("Ctrl+Shift+S")
        icon(Material2AL.LIBRARY_MUSIC)
        executes { activity -> activity.samplesWindow.showOrBringToFront() }
    }
    addAction("Show control buses") {
        shortcut("Ctrl+B")
        icon(Material2AL.GRAPHIC_EQ)
        executes { activity ->
            activity.busesWindow.showOrBringToFront()
            val buses = activity.context[BusRegistry]
            val midiReceiver = activity.context[ContextualMidiReceiver]
            midiReceiver.setContext(ControlBusesMidiReceiver(buses))
        }
    }
})