package ponticello.ui.launcher

import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.actions.registerShortcuts
import fxutils.infiniteSpace
import fxutils.label
import fxutils.prompt.YesNoPrompt
import fxutils.runAfterLayout
import fxutils.styleClass
import hextant.context.Context
import javafx.scene.Parent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.StageStyle
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import ponticello.ui.actions.LauncherActions
import ponticello.ui.actions.WindowActions
import ponticello.ui.impl.sceneFill
import ponticello.ui.impl.showDialog
import java.io.File

class LauncherActivity(private val launcher: PonticelloLauncher) : Activity() {
    override val context: Context
        get() = launcher.rootContext
    private val searchField = CustomTextField().apply {
        styleClass("sleek-text-field", "search-field")
        left = FontIcon(Material2MZ.SEARCH)
        promptText = "Search for project..."
        prefWidth = 200.0
    }

    private val boxes = mutableListOf<ProjectBox>()
    private val recentProjects = VBox().styleClass("recent-projects-list")

    private val quitBtn = ActionBar(
        listOf(WindowActions.quitAction.withContext(launcher)),
        buttonStyle = "large-icon-button"
    )
    private val projectActions = LauncherActions.all.withContext(launcher)

    private val header = HBox(searchField, ActionBar(projectActions, "large-icon-button"), infiniteSpace(), quitBtn)
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
        primaryStage.initStyle(StageStyle.UNDECORATED)
        primaryStage.sceneFill(Color.BLACK)
        primaryStage.isMaximized = false
        primaryStage.title = "Ponticello Launcher"
    }

    override fun afterShowing() {
        runAfterLayout {
            primaryStage.sizeToScene()
            primaryStage.isResizable = false
            primaryStage.centerOnScreen()
        }
        val actions = projectActions + WindowActions.quitAction.withContext(launcher)
        primaryStage.scene.root.registerShortcuts(actions)
        super.afterShowing()
    }

    private class ProjectBox(val activity: LauncherActivity, val projectFile: File) : HBox() {
        private val name = label(projectFile.nameWithoutExtension).styleClass("project-name")
        private val path = label(projectFile.absolutePath).styleClass("project-path")

        init {
            styleClass("project-box")
            children.addAll(
                VBox(name, path),
                infiniteSpace(),
                ActionBar(projectItemActions.withContext(this), "large-icon-button")
            )
            isFocusTraversable = true
            setOnMouseClicked { ev ->
                if (ev.clickCount >= 2) {
                    tryOpen()
                    ev.consume()
                } else requestFocus()
            }
            registerShortcuts(projectItemActions.withContext(this))
            if (!projectFile.isFile) {
                path.textFill = Color.RED
            }
        }

        fun tryOpen() {
            if (projectFile.isDirectory) {
                activity.launcher.openProject(projectFile, askSync = true)
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
        private val projectItemActions = collectActions<ProjectBox> {
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