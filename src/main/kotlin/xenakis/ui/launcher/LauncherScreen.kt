package xenakis.ui.launcher

import hextant.context.Context
import hextant.fx.label
import hextant.fx.runFXWithTimeout
import javafx.scene.Parent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.StageStyle
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import xenakis.ui.actions.*
import xenakis.ui.impl.infiniteSpace
import xenakis.ui.impl.styleClass
import xenakis.ui.prompt.YesNoPrompt
import java.io.File

class LauncherScreen(private val launcher: XenakisLauncher) : Activity() {
    override val context: Context
        get() = launcher.rootContext
    private val searchField = CustomTextField().apply {
        styleClass("sleek-text-field", "search-field")
        left = FontIcon(Material2MZ.SEARCH)
        promptText = "Search for project..."
        minWidth = 200.0
    }

    private val boxes = mutableListOf<ProjectBox>()
    private val recentProjects = VBox().styleClass("recent-projects-list")

    private val openBtn = ProjectActions.openProject.withContext(launcher).makeButton()
    private val createBtn = ProjectActions.newProject.withContext(launcher).makeButton()
    private val quitBtn = ActionBar(QuitAction.withContext(launcher), border = false)
    private val header = HBox(searchField, openBtn, createBtn, infiniteSpace(), quitBtn)
        .styleClass("startup-screen-top-bar")

    init {
        for (proj in launcher.recentProjects.list()) {
            val box = ProjectBox(this, proj)
            boxes.add(box)
        }
        recentProjects.children.addAll(boxes)
        setupSearchFilter()
    }

    private fun setupSearchFilter() {
        searchField.textProperty().addListener { _, _, searchText ->
            recentProjects.children.setAll(boxes.filter { box ->
                box.projectFile.nameWithoutExtension.contains(searchText)
            })
        }
    }

    override fun getLayout(): Parent = VBox(header, recentProjects).styleClass("startup-screen")

    override fun beforeShowing() {
        stage.initStyle(StageStyle.UNDECORATED)
        stage.isMaximized = false
        stage.title = "Xenakis Launcher"
    }

    override fun afterShowing() {
        runFXWithTimeout(100) {
            stage.sizeToScene()
            stage.isResizable = false
            stage.centerOnScreen()
        }
        stage.scene.root.registerShortcuts(launcherActions.withContext(launcher) + QuitAction.withContext(launcher))
    }

    private class ProjectBox(val activity: LauncherScreen, val projectFile: File) : HBox() {
        private val name = label(projectFile.nameWithoutExtension).styleClass("project-name")
        private val path = label(projectFile.absolutePath).styleClass("project-path")

        init {
            styleClass("project-box")
            children.addAll(
                VBox(name, path),
                infiniteSpace(),
                ActionBar(projectActions.withContext(this), border = false)
            )
            isFocusTraversable = true
            setOnMouseClicked { ev ->
                if (ev.clickCount >= 2) {
                    tryOpen()
                    ev.consume()
                } else requestFocus()
            }
            registerShortcuts(projectActions.withContext(this))
            if (!projectFile.isFile) {
                path.textFill = Color.RED
            }
        }

        fun tryOpen() {
            if (projectFile.isDirectory) {
                activity.launcher.openProject(projectFile)
            } else {
                removeFromRecentProjects("Project file does not exist. Remove from list?")
            }
        }

        fun removeFromRecentProjects(question: String) {
            val remove = YesNoPrompt(
                question, default = true
            ).showDialog(activity.launcher.rootContext)
            if (remove == true) {
                activity.launcher.recentProjects.removeFromRecentProjects(projectFile)
                activity.recentProjects.children.remove(this)
            }
        }
    }

    companion object {
        private val launcherActions = collectActions {
            add(ProjectActions.openProject)
            add(ProjectActions.newProject)
        }

        private val projectActions = collectActions<ProjectBox> {
            addAction("Open") {
                description("Open this project")
                icon(Material2MZ.PLAY_ARROW)
                shortcut("ENTER")
                executes { box -> box.tryOpen() }
            }
            addAction("Remove") {
                description("Remove from list of recent projects")
                icon(Material2AL.DELETE)
                shortcut("DELETE")
                executes { box -> box.removeFromRecentProjects(question = "Remove from list of recent projects?") }
            }
        }
    }
}