package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.actions.action
import fxutils.actions.showsContextMenu
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import org.kordamp.ikonli.materialdesign2.MaterialDesignG
import ponticello.impl.Platform
import ponticello.model.project.PonticelloProject
import ponticello.ui.launcher.PonticelloLauncher
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import reaktive.value.binding.impl.notNull
import reaktive.value.reactiveValue

object ProjectActions : Action.Collector<PonticelloLauncher>() {
    val saveProject = action("Save Project") {
        icon(Material2MZ.SAVE)
        shortcut("Ctrl+S")
        executes { launcher: PonticelloLauncher -> launcher.saveProject() }
    }

    val projectUtilityActions = Action.Collector<PonticelloProject> {
        addAction("Open project directory in File Explorer") {
            shortcut("Ctrl+Shift+O")
            icon(Material2OutlinedAL.FOLDER_OPEN)
            executes { project -> Platform.get().openDirectory(project.projectDirectory) }
        }
        addAction("Open a terminal in the project directory") {
            shortcut("Ctrl+Shift+T")
            icon(Codicons.TERMINAL)
            executes { project -> Platform.get().openTerminal(project.projectDirectory) }
        }
        addAction("Open GitHub Desktop") {
            enableWhen { project ->
                if (Platform.get().isCommandAvailable("github-desktop")) reactiveValue(false)
                else project.versionControl.notNull()
            }
            ifNotApplicable(Action.IfNotApplicable.Hide)
            icon(MaterialDesignG.GITHUB)
            executes { project ->
                Platform.get().runCommand("github-desktop", "-p", project.projectDirectory.absolutePath)
            }
        }
    }

    val showUtilityMenu = action("Utility actions") {
        icon(MaterialDesignD.DOTS_VERTICAL)
        showsContextMenu { project -> projectUtilityActions.withContext(project) }
    }

    init {
        add(saveProject)
//        add(openProject)
//        add(newProject)
//        addAction("Close Project") {
//            icon(MaterialDesignC.CLOSE)
//            shortcut("Ctrl+Shift?+W")
//            description("Close project and open the launcher window.")
//            executes { launcher: PonticelloLauncher, ev ->
//                launcher.closeProject(autoSave = ev.isShiftDown())
//            }
//        }
        add(showUtilityMenu) { launcher -> launcher.rootContext[currentProject] }
    }
}