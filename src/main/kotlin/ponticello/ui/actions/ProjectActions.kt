package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.actions.action
import fxutils.actions.isShiftDown
import fxutils.actions.showsContextMenu
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import ponticello.impl.Platform
import ponticello.model.project.PonticelloProject
import ponticello.ui.launcher.PonticelloLauncher

object ProjectActions : Action.Collector<PonticelloProject>() {
    val saveProject = action<PonticelloProject>("Save Project") {
        icon(Material2MZ.SAVE)
        shortcut("Ctrl+S")
        executes { project -> project.save() }
    }

    val projectUtilityActions = Action.Collector<PonticelloProject> {
        addAction("Open in File Explorer") {
            shortcut("Ctrl+Shift+O")
            icon(Material2OutlinedAL.FOLDER_OPEN)
            executes { project -> Platform.get().openDirectory(project.projectDirectory) }
        }
        addAction("Open in terminal") {
            shortcut("Ctrl+Shift+T")
            icon(Codicons.TERMINAL)
            executes { project -> Platform.get().openTerminal(project.projectDirectory) }
        }
    }

    val showUtilityMenu = action("Utility actions") {
        icon(MaterialDesignD.DOTS_VERTICAL)
        showsContextMenu { project -> projectUtilityActions.withContext(project) }
    }

    init {
        add(saveProject)
        addAction("Close Project") {
//            icon(MaterialDesignC.CLOSE)
            shortcut("Ctrl+Shift?+W")
            description("Close project and open the launcher window.")
            executes { project: PonticelloProject, ev ->
                val launcher = project.context[PonticelloLauncher]
                launcher.closeProject(autoSave = ev.isShiftDown())
            }
        }
        add(showUtilityMenu)
    }
}