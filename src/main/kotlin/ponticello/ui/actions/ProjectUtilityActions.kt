package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.actions.action
import fxutils.actions.showsContextMenu
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import ponticello.model.project.PonticelloProject

object ProjectUtilityActions : Action.Collector<PonticelloProject>({
    addAction("Show project in File Explorer") {
        shortcut("Ctrl+Shift+O")
        executes { project -> project.openInExplorer() }
    }
}) {
    val menuAction = action("Utility actions") {
        icon(MaterialDesignD.DOTS_VERTICAL)
        showsContextMenu { project -> ProjectUtilityActions.withContext(project) }
    }
}