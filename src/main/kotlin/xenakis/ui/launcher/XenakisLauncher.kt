package xenakis.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.SubWindow
import fxutils.prompt.PredicateTextPrompt
import fxutils.prompt.YesNoPrompt
import hextant.context.Context
import hextant.context.extend
import hextant.core.HextantCore
import hextant.fx.Stylesheets
import hextant.plugins.PluginBuilder
import hextant.serial.writeJson
import hextant.undo.UndoManager
import javafx.application.Platform
import javafx.stage.Stage
import xenakis.impl.registerImplementationsFromClasspath
import xenakis.impl.Logger
import xenakis.model.Settings
import xenakis.model.project.XenakisProject
import xenakis.model.registry.GlobalSynthDefLib
import xenakis.sc.client.OSCSuperColliderClient
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.impl.showDialog
import xenakis.ui.impl.tryWithAlert
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
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
        SubWindow.globalStylesheets.addAll(get(Stylesheets).all())
    }

    private lateinit var currentActivity: Activity

    private fun <A : Activity> launchActivity(description: String, createActivity: () -> A): A? {
        val stage = Stage()
        stage.setOnCloseRequest { closeRequest() }
        rootContext[primaryStage] = stage
        val activity = try {
            createActivity()
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.error("Error $description")
            showLauncher()
            return null
        }
        currentActivity.hide()
        currentActivity = activity
        activity.show(stage)
        return activity
    }

    fun openProject() {
        val file = rootContext[XenakisFiles].showOpenDialog("*.xen") ?: return
        openProject(file.parentFile)
    }

    fun openProject(folder: File) {
        setupProjectContext(
            "opening project",
            whenReady = { context ->
                val project = XenakisProject.loadFrom(folder, context, getOrLaunchLoadingScreen())
                openProject(project)
            }, onError = {
                recentProjects.clearActiveProject()
            }
        )
    }

    private fun getOrLaunchLoadingScreen() =
        currentActivity as? LoadingScreen ?: launchActivity("Showing loading screen") { LoadingScreen(rootContext) }!!

    private fun getActiveProject(): XenakisProject? = (currentActivity as? XenakisMainActivity)?.project

    private fun openProject(project: XenakisProject) {
        rootContext[UndoManager].reset()
        rootContext[currentProject] = project
        recentProjects.push(project.projectDirectory)
        Platform.runLater {
            val activity = launchActivity("Open project ${project.name}") { XenakisMainActivity(project) }
            if (activity == null) {
                project.client.quit()
            }
        }
    }

    fun closeProject() {
        val save = askIfUserWantsToSave() ?: return
        if (save) saveProject()
        recentProjects.clearActiveProject()
        showLauncher()
    }

    fun createNewProject() {
        val name =
            PredicateTextPrompt("Project name", "") { name -> name.isNotBlank() }.showDialog(rootContext) ?: return
        val location = rootContext[XenakisFiles].userHome.resolve("compositions")
            .resolve(name) //TODO introduce option for projects location
        location.mkdir()
        location.resolve("project.xen").writeText(location.name)
        createNewProject(location)
    }

    private fun createNewProject(location: File) {
        setupProjectContext("creating new project") { context ->
            val project = XenakisProject.create(location, context)
            save(project, location)
            openProject(project)
        }
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
            openProject(file)
        } else {
            showLauncher()
        }
    }

    private fun setupProjectContext(description: String, onError: () -> Unit = {}, whenReady: (Context) -> Unit) {
        val indicator = getOrLaunchLoadingScreen()
        indicator.displayProgress(0.0, "Starting SuperCollider")
        try {
            val client = OSCSuperColliderClient.create()
            val context = rootContext.extend {
                set(SuperColliderClient, client)
            }
            context[SuperColliderClient].bootServer(indicator) {
                try {
                    whenReady(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Logger.error("Error $description")
                    client.quit()
                    onError()
                    showLauncher()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.error("Error starting SuperCollider", Logger.Category.Project, e.message)
            showLauncher()
        }
    }

    private fun showLauncher() = Platform.runLater {
        launchActivity("Show Xenakis launcher") { LauncherActivity(this) }
    }

    fun closeRequest(automaticallySave: Boolean = false) {
        if (getActiveProject() == null) {
            currentActivity.hide()
            return
        }
        //TODO check if any edits have been made since the last save
        val save = automaticallySave || askIfUserWantsToSave() ?: return
        if (save) saveProject()
        currentActivity.hide()
        quitApplication()
    }

    private fun askIfUserWantsToSave() =
        YesNoPrompt("Save project before exiting?", cancellable = true, default = null)
            .showDialog(rootContext)

    fun quitApplication() {
        rootContext[XenakisFiles].resolve("settings.json").writeJson(rootContext[Settings])
        exitProcess(0)
    }

    companion object : PublicProperty<XenakisLauncher> by publicProperty("xenakis-launcher") {
        val currentProject = publicProperty<XenakisProject>("current-project")
    }
}