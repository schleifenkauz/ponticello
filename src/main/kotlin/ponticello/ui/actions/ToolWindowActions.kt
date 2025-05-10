package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.actions.isShiftDown
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.SimpleTextPrompt
import javafx.scene.layout.Region
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.*
import ponticello.impl.Logger
import ponticello.model.ScriptObject
import ponticello.ui.impl.NotificationView
import ponticello.ui.impl.showDialog
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.misc.HelpBrowser

object ToolWindowActions : Action.Collector<PonticelloMainActivity>({
    addAction("Open console") {
        shortcut("Ctrl+Alt+C")
        icon(MaterialDesignC.CONSOLE)
        executes { activity -> activity.shellWindow.showOrBringToFront() }
    }
    addAction("Show log window") {
        shortcut("Ctrl+Alt+L")
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
    addAction("Open help browser") {
        shortcut("F1")
        icon(MaterialDesignW.WEB)
        executes { activity -> activity.context[HelpBrowser].show() }
    }
    addAction("Edit scratch file") {
        icon(MaterialDesignF.FILE_COG)
        shortcut("Ctrl+K")
        executes { activity, ev ->
            val list = SimpleSearchableListView(ScriptObject.Type.entries, "Open scratch file")
            val source = ev?.source as? Region
            val anchor = source?.localToScreen(0.0, source.height)
            val type = list.showPopup(anchor, owner = activity.primaryStage) ?: return@executes
            val window = activity.scriptObjectWindows.getValue(type)
            window.showOrBringToFront()
        }
    }
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
    addAction("Show ScoreObjects") {
        shortcut("Ctrl+Shift+O")
        icon(MaterialDesignP.PLAYLIST_PLAY)
        executes { activity -> activity.scoreObjectsWindow.showOrBringToFront() }
    }
    addAction("Show SynthDefs") {
        icon(MaterialDesignS.SINE_WAVE)
        shortcut("Ctrl+I")
        executes { activity -> activity.synthDefsWindow.showOrBringToFront() }
    }
    addAction("Show ProcessDefs") {
        shortcut("Ctrl+P")
        icon(Codicons.SERVER_PROCESS)
        executes { activity -> activity.processDefsWindow.showOrBringToFront() }
    }
    addAction("Show audio flows") {
        shortcut("Ctrl+Shift+F")
        icon(MaterialDesignT.TUNE)
        executes { activity -> activity.flowPaneWindow.showOrBringToFront() }
    }
    addAction("Show samples") {
        shortcut("Ctrl+Shift+S")
        icon(Material2AL.LIBRARY_MUSIC)
        executes { activity -> activity.samplesWindow.showOrBringToFront() }
    }
    addAction("Show buffers") {
        shortcut("Ctrl+Alt+B")
        icon(MaterialDesignB.BUFFER)
        executes { activity -> activity.buffersWindow.showOrBringToFront() }
    }
    addAction("Show control buses") {
        shortcut("Ctrl+B")
        icon(MaterialDesignT.TUNE_VARIANT)
        executes { activity -> activity.controlBusWindow.showOrBringToFront() }
    }
    addAction("Show audio buses") {
        shortcut("Ctrl+Shift+B")
        icon(Material2AL.GRAPHIC_EQ)
        executes { activity ->
            activity.audioBusWindow.showOrBringToFront()
        }
    }
    addAction("Show global Patterns") {
        shortcut("Ctrl+Shift+P")
        icon(MaterialDesignL.LARAVEL)
        executes { activity -> activity.patternsWindow.showOrBringToFront() }
    }
    addAction("Show Live Tasks") {
        shortcut("Ctrl+Shift+T")
        icon(MaterialDesignP.PROGRESS_QUESTION)
        executes { activity -> activity.liveTasksWindow.showOrBringToFront() }
    }
    addAction("Show Launcher Grid") {
        shortcut("Ctrl+G")
        icon(MaterialDesignG.GRID)
        executes { activity -> activity.launcherGridWindow.showOrBringToFront() }
    }
})