package xenakis.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.context.extend
import hextant.core.HextantCore
import hextant.plugins.PluginBuilder
import hextant.serial.SnapshotAware
import hextant.serial.writeJson
import hextant.undo.UndoManager
import javafx.application.Platform
import javafx.stage.Stage
import xenakis.impl.registerImplementationsFromClasspath
import xenakis.model.Logger
import xenakis.model.Settings
import xenakis.model.XenakisProject
import xenakis.model.registry.GlobalSynthDefLib
import xenakis.sc.client.OSCSuperColliderClient
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.XenakisApp.Companion.primaryStage
import xenakis.ui.XenakisHextantPlugin
import xenakis.ui.XenakisMainScreen
import xenakis.ui.impl.tryWithAlert
import xenakis.ui.prompt.PredicateTextPrompt
import xenakis.ui.prompt.YesNoPrompt
import java.io.File
import kotlin.system.exitProcess

class XenakisLauncher {
    val recentProjects = RecentProjects()

    val rootContext: Context = HextantCore.defaultContext().apply {
        set(XenakisLauncher, this@XenakisLauncher)
        val files = XenakisFiles(this)
        set(XenakisFiles, files)
        set(Settings, files.loadSettings())
        set(GlobalSynthDefLib, GlobalSynthDefLib(this, files.resolve("synth-def-lib.json")))
        registerImplementationsFromClasspath()
        HextantCore.apply(this, PluginBuilder.Phase.Initialize, null)
        XenakisHextantPlugin.apply(this, PluginBuilder.Phase.Initialize, null)
    }

    private lateinit var currentActivity: Activity

    private fun launchActivity(activity: Activity) {
        currentActivity.hide()
        currentActivity = activity
        val stage = Stage()
        stage.setOnCloseRequest { closeRequest() }
        rootContext[primaryStage] = stage
        activity.show(stage)
    }

    fun openProject() {
        val file = rootContext[XenakisFiles].showOpenDialog("*.xen") ?: return
        openProject(file.parentFile)
    }

    fun openProject(folder: File): Boolean {
        val loadingScreen = getOrLaunchLoadingScreen()
        tryWithAlert("Opening project", category = Logger.Category.Project) {
            Logger.clear()
            val context = setupProjectContext(loadingScreen)
            context[SuperColliderClient].bootServer(loadingScreen) {
                val project = XenakisProject.loadFrom(folder, context, loadingScreen)
                Platform.runLater {
                    openProject(project)
                }
            }
        } ?: return false
        return true
    }

    private fun getOrLaunchLoadingScreen() =
        currentActivity as? LoadingScreen ?: LoadingScreen(rootContext).also(::launchActivity)

    fun getActiveProject(): XenakisProject? = (currentActivity as? XenakisMainScreen)?.project

    private fun openProject(project: XenakisProject) {
        rootContext[UndoManager].reset()
        rootContext[currentProject] = project
        recentProjects.push(project.projectDirectory)
        launchActivity(XenakisMainScreen(project))
    }

    fun closeProject() {
        recentProjects.clearActiveProject()
        launchActivity(LauncherScreen(this))
    }

    fun createNewProject() {
        val name =
            PredicateTextPrompt("Project name", "") { name -> name.isNotBlank() }.showDialog(rootContext) ?: return
        val location = rootContext[XenakisFiles].userHome.resolve("compositions")
            .resolve(name) //TODO introduce option for projects location
        location.mkdir()
        location.resolve("project.xen").writeText(location.name)
        val context = setupProjectContext(getOrLaunchLoadingScreen())
        val project = XenakisProject.create(location, context)
        save(project, location)
        openProject(project)
    }

    fun saveProject() {
        val project = rootContext[currentProject]
        val file = recentProjects.activeProject() ?: rootContext[XenakisFiles].showOpenDialog("*.xen") ?: return
        tryWithAlert("Saving score") { save(project, file) }
        Logger.confirm("Saved project ${project.projectDirectory.name}", Logger.Category.Project)
    }

    private fun save(project: XenakisProject, folder: File) {
        project.saveTo(folder)
        recentProjects.push(folder)
    }

    fun launchXenakis(primaryStage: Stage) {
        primaryStage.setOnCloseRequest { closeRequest() }
        currentActivity = LoadingScreen(rootContext)
        currentActivity.show(primaryStage)
        val file = recentProjects.activeProject()
        if (file != null) {
            if (!file.exists() || !openProject(file)) {
                recentProjects.removeFromRecentProjects(file)
                launchActivity(LauncherScreen(this))
            }
        } else {
            launchActivity(LauncherScreen(this))
        }
    }

    private fun setupProjectContext(indicator: ProgressIndicator): Context {
        indicator.displayProgress(0.0, "Starting SuperCollider")
        val context = rootContext.extend {
            set(SuperColliderClient, OSCSuperColliderClient.create())
        }
        SnapshotAware.Serializer.reconstructionContext = context
        return context
    }


    fun closeRequest() {
        if (getActiveProject() == null) {
            currentActivity.hide()
            return
        }
        //TODO check if any edits have been made since the last save
        val save = YesNoPrompt("Save project before exiting?", cancellable = true, default = true)
            .showDialog(rootContext) ?: return
        if (save) saveProject()
        currentActivity.hide()
    }

    fun quitApplication() {
        rootContext[XenakisFiles].resolve("settings.json").writeJson(rootContext[Settings])
        exitProcess(0)
    }

    companion object : PublicProperty<XenakisLauncher> by publicProperty("xenakis-launcher") {
        val currentProject = publicProperty<XenakisProject>("current-project")
    }
}