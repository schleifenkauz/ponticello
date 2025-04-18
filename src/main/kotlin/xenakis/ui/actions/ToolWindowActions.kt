package xenakis.ui.actions

import fxutils.actions.Action
import fxutils.actions.isShiftDown
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.SimpleTextPrompt
import javafx.scene.layout.Region
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.*
import xenakis.impl.Logger
import xenakis.model.ScratchFile
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
    addAction("Edit scratch file") {
        icon(MaterialDesignF.FILE_COG)
        shortcut("Ctrl+K")
        executes { activity, ev ->
            val list = SimpleSearchableListView(ScratchFile.Type.entries, "Open scratch file")
            val source = ev?.source as? Region
            val anchor = source?.localToScreen(0.0, source.height)
            val type = list.showPopup(anchor, owner = activity.primaryStage) ?: return@executes
            val window = activity.scratchFileWindows.getValue(type)
            window.showOrBringToFront()
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
    addAction("Show buffers") {
        shortcut("Ctrl+Shift+B")
        icon(MaterialDesignB.BUFFER)
        executes { activity -> activity.buffersWindow.showOrBringToFront() }
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
    addAction("Show global Patterns") {
        shortcut("Ctrl+Shift+P")
        icon(MaterialDesignL.LARAVEL)
        executes { activity -> activity.patternsWindow.showOrBringToFront() }
    }
})