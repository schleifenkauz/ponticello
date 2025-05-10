package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.actions.action
import fxutils.actions.isShiftDown
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.ui.launcher.PonticelloLauncher

object ProjectActions : Action.Collector<PonticelloLauncher>() {
    val openProject = action("Open Project") {
        icon(Codicons.FOLDER_OPENED)
        shortcut("Ctrl+O")
        executes { launcher: PonticelloLauncher -> launcher.openProject() }
    }

    val newProject = action("Create New Project") {
        icon(Material2OutlinedAL.CREATE_NEW_FOLDER)
        shortcut("Ctrl+N")
        executes { launcher: PonticelloLauncher -> launcher.createNewProject() }
    }

    init {
        addAction("Save Project") {
            icon(Material2MZ.SAVE)
            shortcut("Ctrl+S")
            executes { launcher: PonticelloLauncher -> launcher.saveProject() }
        }
        add(openProject)
        add(newProject)
        addAction("Close Project") {
            icon(MaterialDesignC.CLOSE)
            shortcut("Ctrl+Shift?+W")
            description("Close project and open the launcher window.")
            executes { launcher: PonticelloLauncher, ev ->
                launcher.closeProject(autoSave = ev.isShiftDown())
            }
        }
    }
}