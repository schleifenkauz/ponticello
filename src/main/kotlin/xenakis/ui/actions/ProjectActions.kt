package xenakis.ui.actions

import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import xenakis.ui.launcher.XenakisLauncher

object ProjectActions: Action.Collector<XenakisLauncher>({
    addAction("Save Project") {
        icon(Material2MZ.SAVE)
        shortcut("Ctrl+S")
        executes { launcher: XenakisLauncher -> launcher.saveProject() }
    }
    addAction("Open Project") {
        icon(Codicons.FOLDER_OPENED)
        shortcut("Ctrl+O")
        executes { launcher: XenakisLauncher -> launcher.openProject() }
    }
    addAction("Create New Project") {
        icon(Material2OutlinedAL.CREATE_NEW_FOLDER)
        shortcut("Ctrl+N")
        executes { launcher: XenakisLauncher -> launcher.createNewProject() }
    }
    addAction("Close Project") {
        icon(MaterialDesignC.CLOSE)
        description("Close project and open the launcher window.")
        executes { launcher: XenakisLauncher -> launcher.closeProject() }
    }

})