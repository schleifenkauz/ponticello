package xenakis.ui.impl

import hextant.fx.Shortcut
import hextant.fx.shortcut
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import xenakis.ui.launcher.XenakisLauncher

sealed class ProjectAction(
    description: String, shortcut: Shortcut, icon: Ikon,
    private val execute: (launcher: XenakisLauncher) -> Unit
) : Action(Category.File, description, shortcut, icon, { context -> execute(context[XenakisLauncher]) }) {

    data object Save : ProjectAction(
        "Save project", "Ctrl+S".shortcut, MaterialDesignC.CONTENT_SAVE,
        { launcher -> launcher.saveProject() }
    )
}