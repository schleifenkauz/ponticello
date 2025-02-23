package xenakis.ui.launcher

import hextant.context.Context
import hextant.fx.label
import hextant.fx.runFXWithTimeout
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2MZ
import xenakis.ui.actions.button
import xenakis.ui.impl.button
import xenakis.ui.impl.infiniteSpace
import xenakis.ui.impl.styleClass
import xenakis.ui.prompt.YesNoPrompt
import java.io.File

class LauncherScreen(private val launcher: XenakisLauncher): Activity() {
    override val context: Context
        get() = launcher.rootContext
    private val searchField = CustomTextField().apply {
        styleClass("sleek-text-field", "search-field")
        left = FontIcon(Material2MZ.SEARCH)
        promptText = "Search for project..."
    }

    private val btnOpen = button("Open") { launcher.openProject() }
    private val createNew = button("Create new") { launcher.createNewProject() }
    private val recentProjects = VBox().styleClass("recent-projects-list")

    private val top = HBox(searchField, btnOpen, createNew).styleClass("startup-screen-top-bar")

    init {
        for (proj in launcher.recentProjects.list()) {
            val box = displayRecentProject(proj)
            recentProjects.children.add(box)
        }
    }

    override fun getLayout(): Parent = VBox(top, recentProjects).styleClass("startup-screen")

    override fun beforeShowing() {
        stage.isMaximized = false
        stage.title = "Xenakis Launcher"
    }

    override fun afterShowing() {
        runFXWithTimeout(100) {
            stage.sizeToScene()
            stage.centerOnScreen()
        }
        stage.isResizable = false
    }


    private fun displayRecentProject(proj: File): HBox {
        val name = label(proj.nameWithoutExtension).styleClass("project-name")
        val path = label(proj.absolutePath).styleClass("project-path")
        val vertical = VBox(name, path)
        if (!proj.isFile) {
            path.textFill = Color.RED
        }
        val box = HBox(vertical).styleClass("project-box")
        box.setOnMouseClicked {
            if (proj.isDirectory) {
                launcher.openProject(proj)
            } else {
                removeFromRecentProjects("Project file does not exist. Remove from list?", proj, box)
            }
        }
        val removeBtn = Material2MZ.SAVE.button(action = "Remove from list of recent projects") {
            removeFromRecentProjects("Remove project from list of recent projects?", proj, box)
        }
        val space = infiniteSpace()
        box.children.addAll(space, removeBtn)
        box.alignment = Pos.CENTER_LEFT
        return box
    }

    private fun removeFromRecentProjects(question: String, proj: File, box: HBox) {
        val remove = YesNoPrompt(
            question, default = true
        ).showDialog(launcher.rootContext)
        if (remove == true) {
            launcher.recentProjects.removeFromRecentProjects(proj)
            recentProjects.children.remove(box)
        }
    }

}