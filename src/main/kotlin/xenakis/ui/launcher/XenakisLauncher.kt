package xenakis.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.transport.OSCPortOut
import fxutils.SubWindow
import fxutils.prompt.PredicateTextPrompt
import fxutils.prompt.YesNoPrompt
import hextant.context.Context
import hextant.context.extend
import hextant.core.HextantCore
import hextant.fx.Stylesheets
import hextant.plugins.PluginBuilder
import hextant.serial.readJson
import hextant.serial.writeJson
import hextant.undo.UndoManager
import javafx.application.Platform
import javafx.stage.Stage
import kotlinx.serialization.serializer
import reaktive.Observer
import xenakis.impl.Logger
import xenakis.impl.registerImplementationsFromClasspath
import xenakis.model.ServerOptions
import xenakis.model.Settings
import xenakis.model.project.SERVER_OPTIONS
import xenakis.model.project.XenakisProject
import xenakis.model.project.XenakisProject.Companion.projectDirectory
import xenakis.model.project.get
import xenakis.model.registry.GlobalDefinitionLibrary
import xenakis.sc.client.ConsoleMonitor
import xenakis.sc.client.OSCSuperColliderClient
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.impl.showDialog
import xenakis.ui.impl.tryWithAlert
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.midi.ContextualMidiReceiver
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class XenakisLauncher {
    val recentProjects = RecentProjects()

    val rootContext: Context = HextantCore.defaultContext().apply {
        set(XenakisLauncher, this@XenakisLauncher)
        val files = XenakisFiles(this)
        set(XenakisFiles, files)
        set(Settings, files.loadSettings())
        get(Settings).initialize(this)
        set(
            GlobalDefinitionLibrary.synthDefs, GlobalDefinitionLibrary(
                files.resolve("synth-def-lib"),
                serializer(), objectType = "SynthDef"
            )
        )
        set(
            GlobalDefinitionLibrary.processDefs, GlobalDefinitionLibrary(
                files.resolve("process-def-lib"),
                serializer(), objectType = "ProcessDef"
            )
        )
        registerImplementationsFromClasspath()
        HextantCore.apply(this, PluginBuilder.Phase.Initialize, null)
        XenakisHextantPlugin.apply(this, PluginBuilder.Phase.Initialize, null)
        SubWindow.globalStylesheets.addAll(get(Stylesheets).all())
        val midiReceiver = ContextualMidiReceiver()
        midiReceiver.initialize("Xjam")
        set(ContextualMidiReceiver, midiReceiver)
    }

    private lateinit var currentActivity: Activity

    private fun <A : Activity> launchActivity(description: String, createActivity: () -> A): A? {
        val stage = Stage()
        stage.setOnCloseRequest { closeRequest() }
        rootContext[primaryStage] = stage
        val activity = try {
            createActivity()
        } catch (e: Exception) {
            Logger.error("Error $description", e)
            e.printStackTrace()
            showLauncher()
            return null
        }
        currentActivity.hide()
        currentActivity = activity
        activity.show(stage)
        return activity
    }

    fun openProject() {
        if (getActiveProject() != null) {
            val save = askIfUserWantsToSave() ?: return
            if (save) saveProject()
        }
        val file = rootContext[XenakisFiles].showOpenDialog("*.xen") ?: return
        openProject(file.parentFile)
    }

    fun openProject(folder: File) {
        val context = rootContext.extend()
        val progressBar = getOrLaunchLoadingScreen()
        setupSuperCollider(
            context,
            "opening project",
            clientReady = { client ->
                val serverOptions = folder.resolve("xenakis_data").resolve("server_options.json")
                    .readJson<ServerOptions>()
                serverOptions.reboot(client)
            },
            serverReady = {
                progressBar.displayProgress(0.2, "Booted SuperCollider server, opening project...")
                thread {
                    context[projectDirectory] = folder
                    val project = XenakisProject.loadFrom(folder, progressBar, targetProgress = 0.9)
                    progressBar.displayProgress(0.95, "Loaded project, initializing...")
                    project.initialize(context)
                    project[SERVER_OPTIONS].configureIOBuses()
                    openProject(project)
                }
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
        getOrLaunchLoadingScreen().displayProgress(0.98, "Almost ready, launching project view...")
        Platform.runLater {
            val activity = launchActivity("Open project ${project.name}") { XenakisMainActivity(project) }
            if (activity == null) {
                project.client.quit()
            }
        }
    }

    fun closeProject(autoSave: Boolean = false) {
        val save = autoSave || askIfUserWantsToSave() ?: return
        if (save) saveProject()
        recentProjects.clearActiveProject()
        showLauncher()
    }

    fun createNewProject() {
        if (getActiveProject() != null) {
            val save = askIfUserWantsToSave() ?: return
            if (save) saveProject()
        }
        val name =
            PredicateTextPrompt("Project name", "") { name -> name.isNotBlank() }.showDialog(rootContext) ?: return
        val location = rootContext[XenakisFiles].projectsDir.resolve(name)
        location.mkdir()
        location.resolve("project.xen").writeText(location.name)
        createNewProject(location)
    }

    private fun createNewProject(location: File) {
        val context = rootContext.extend()
        val progressBar = getOrLaunchLoadingScreen()
        setupSuperCollider(
            context,
            "creating new project",
            clientReady = { client -> client.run("s.boot") },
            serverReady = {
                progressBar.displayProgress(0.2, "Booted SuperCollider server, creating new project...")
                val project = XenakisProject.create(location, context)
                save(project, location)
                progressBar.displayProgress(0.3, "Created new project...")
                openProject(project)
            })
    }

    fun saveProject() {
        val project = rootContext[currentProject]
        val file = recentProjects.activeProject() ?: rootContext[XenakisFiles].showOpenDialog("*.xen") ?: return
        tryWithAlert("Saving project") { save(project, file) }
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

    private fun setupSuperCollider(
        context: Context,
        description: String,
        clientReady: (SuperColliderClient) -> Unit,
        serverReady: (SuperColliderClient) -> Unit,
    ) {
        val progressBar = getOrLaunchLoadingScreen()
        progressBar.displayProgress(0.0, "Starting SuperCollider...")
        try {
            val client = OSCSuperColliderClient.create(scPort = OSCPortOut.DEFAULT_SC_LANG_OSC_PORT)
            client.consoleMonitor.addListener(ConsoleMonitor.PipeToSystemOut)
            client.onClientReady {
                Platform.runLater {
                    progressBar.displayProgress(0.1, "SuperCollider started, booting server...")
                    try {
                        context[SuperColliderClient] = client
                        clientReady(client)
                    } catch (e: Exception) {
                        Logger.error("Error while $description")
                        client.quit()
                        recentProjects.clearActiveProject()
                        showLauncher()
                    }
                }
            }
            lateinit var bootObserver: Observer
            bootObserver = client.onServerBooted {
                Platform.runLater {
                    try {
                        serverReady(client)
                        bootObserver.kill()
                    } catch (e: Exception) {
                        Logger.error("Error while $description")
                        e.printStackTrace()
                        recentProjects.clearActiveProject()
                        client.quit()
                        showLauncher()
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("Error starting SuperCollider", e, Logger.Category.Project)
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