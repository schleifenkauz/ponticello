package ponticello.ui.actions

import fxutils.actions.action
import fxutils.actions.collectActions
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.materialdesign2.MaterialDesignG
import ponticello.ui.launcher.PonticelloLauncher

object LauncherActions {
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

    val cloneProject = action("Clone from Git repository") {
        icon(MaterialDesignG.GIT)
        shortcut("Ctrl+Shift+O")
        executes { launcher: PonticelloLauncher -> launcher.cloneRepository() }
    }

    val all = collectActions {
        add(openProject)
        add(newProject)
        add(cloneProject)
    }
}