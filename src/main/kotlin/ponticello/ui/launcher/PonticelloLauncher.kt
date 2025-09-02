package ponticello.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.transport.OSCPortOut
import fxutils.SubWindow
import fxutils.prompt.PredicateTextPrompt
import fxutils.prompt.YesNoPrompt
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.extend
import hextant.core.HextantCore
import hextant.fx.Stylesheets
import hextant.plugins.PluginBuilder
import hextant.serial.readJson
import hextant.serial.writeJson
import javafx.application.Platform
import javafx.stage.Stage
import kotlinx.serialization.serializer
import ponticello.impl.Logger
import ponticello.impl.json
import ponticello.impl.registerImplementationsFromClasspath
import ponticello.model.GlobalSettings
import ponticello.model.ServerOptions
import ponticello.model.flow.NodeTree
import ponticello.model.obj.ScriptObject
import ponticello.model.obj.project
import ponticello.model.player.ActiveObjectsManager
import ponticello.model.player.Recorder
import ponticello.model.player.ScoreObjectScheduler
import ponticello.model.player.ScorePlayer
import ponticello.model.project.PonticelloProject
import ponticello.model.project.PonticelloProject.Companion.projectDirectory
import ponticello.model.project.SERVER_OPTIONS
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import ponticello.model.registry.GlobalDefinitionLibrary
import ponticello.sc.client.ConsoleMonitor
import ponticello.sc.client.OSCSuperColliderClient
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.showDialog
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.midi.ContextualMidiReceiver
import ponticello.ui.score.ScoreObjectDuplicator
import reaktive.Observer
import reaktive.value.now
import java.io.File
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.system.exitProcess

class PonticelloLauncher {
    val recentProjects = RecentProjects()
    val rootContext get() = PonticelloLauncher.rootContext

    init {
        rootContext[PonticelloLauncher] = this
    }

    private lateinit var currentActivity: Activity

    private fun <A : Activity> launchActivity(description: String, createActivity: () -> A): A? {
        currentActivity.hide()
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
        currentActivity = activity
        activity.show(stage)
        return activity
    }

    fun openProject() {
        if (saveChanges() == null) return
        val file = rootContext[PonticelloFiles].showOpenDialog("*.pont") ?: return
        openProject(file.parentFile)
    }

    private fun saveChanges(autoSave: Boolean = false): Boolean? {
        val project = getActiveProject() ?: return false
        project.context[AppLayout].saveLayoutState()
        project[UI_STATE].saveWindowStates()
        project.save(UI_STATE)
        if (autoSave) {
            val ok = saveProject()
            if (!ok) return null
            return true
        }
        if (!project.context[UndoManager].hasUnsavedChanges.now) return false
        val save = askIfUserWantsToSave() ?: return null
        if (save) {
            val ok = saveProject()
            if (!ok) return null
            return false
        }
        return false
    }

    fun openProject(folder: File) {
        val context = rootContext.extend()
        context[UndoManager] = UndoManager.newInstance()
        val progressBar = getOrLaunchLoadingScreen()
        setupSuperCollider(
            context,
            "opening project",
            clientReady = { client ->
                val beforeBootFile = folder.resolve("data/scripts").resolve("before_boot.json")
                if (beforeBootFile.exists()) {
                    try {
                        val beforeBoot = beforeBootFile.readJson(ScriptObject.serializer())
                        beforeBoot.initialize(context)
                        beforeBoot.executeContents(client).join()
                    } catch (e: Exception) {
                        Logger.error("Error while executing setup script: $beforeBootFile", e)
                    }
                }
                val serverOptionsFile = folder.resolve("data").resolve("server_options.json")
                if (serverOptionsFile.exists()) {
                    val serverOptions = serverOptionsFile.readJson<ServerOptions>(json)
                    serverOptions.configureOptions(client)
                }
                client.run("s.boot")
            },
            serverReady = {
                progressBar.displayProgress(0.2, "Booted SuperCollider server, opening project...")
                thread(isDaemon = true) {
                    context[projectDirectory] = folder
                    val project = PonticelloProject.loadFrom(folder, progressBar, targetProgress = 0.5)
                    progressBar.displayProgress(0.5, "Loaded project, initializing...")
                    project.initialize(context, progressBar, totalDeltaProgress = 0.45)
                    project[SERVER_OPTIONS].configureIOBuses()
                    openProject(project)
                }
            }
        )
    }

    private fun getOrLaunchLoadingScreen() =
        currentActivity as? LoadingScreen ?: launchActivity("Showing loading screen") { LoadingScreen(rootContext) }!!

    private fun getActiveProject(): PonticelloProject? = (currentActivity as? PonticelloMainActivity)?.project

    private fun openProject(project: PonticelloProject) {
        ScorePlayer.clearInstances()
        rootContext[ContextualMidiReceiver].clearMidiContext()
        rootContext[currentProject] = project
        recentProjects.push(project.projectDirectory)
        getOrLaunchLoadingScreen().displayProgress(0.98, "Almost ready, launching project view...")
        Platform.runLater {
            val activity = launchActivity("Open project ${project.name}") { PonticelloMainActivity(project) }
            if (activity == null) {
                project.client.quit()
            }
        }
    }

    fun closeProject(autoSave: Boolean = false) {
        saveChanges(autoSave) ?: return
        recentProjects.clearActiveProject()
        showLauncher()
    }

    fun createNewProject() {
        saveChanges() ?: return
        val name = PredicateTextPrompt("Project name", "") { name -> name.isNotBlank() }
            .showDialog(rootContext) ?: return
        val location = PonticelloFiles.projectsDir.resolve(name)
        location.mkdir()
        location.resolve("project.pont").writeText(location.name)
        createNewProject(location)
    }

    private fun createNewProject(location: File) {
        val context = rootContext.extend()
        context[UndoManager] = UndoManager.newInstance()
        val progressBar = getOrLaunchLoadingScreen()
        setupSuperCollider(
            context,
            "creating new project",
            clientReady = { client -> client.run("s.boot") },
            serverReady = {
                progressBar.displayProgress(0.2, "Booted SuperCollider server, creating new project...")
                val project = PonticelloProject.create(location, context, progressBar, totalDeltaProgress = 0.7)
                save(project, location)
                progressBar.displayProgress(0.95, "Created new project...")
                openProject(project)
            })
    }

    fun saveProject(): Boolean {
        val project = rootContext.project
        val file = recentProjects.activeProject() ?: rootContext[PonticelloFiles].showOpenDialog("*.pont") ?: return false
        val ok = save(project, file)
        if (ok) {
            Logger.confirm("Saved project ${project.projectDirectory.name}", Logger.Category.Project)
        } else {
            Logger.error("Failed to save project ${project.projectDirectory.name}", Logger.Category.Project)
        }
        return ok
    }

    private fun save(project: PonticelloProject, folder: File): Boolean {
        val ok = project.saveTo(folder)
        recentProjects.push(folder)
        return ok
    }

    fun launchPonticello(primaryStage: Stage, projectPath: String?) {
        primaryStage.setOnCloseRequest { closeRequest() }
        currentActivity = LoadingScreen(rootContext)
        currentActivity.show(primaryStage)
        val projectFromCLArgs = when {
            projectPath == null -> null
            projectPath.startsWith("/") -> File(projectPath)
            projectPath.startsWith("~/") -> PonticelloFiles.userHome.resolve(projectPath.drop(2))
            else -> PonticelloFiles.projectsDir.resolve(projectPath)
        }
        val file = projectFromCLArgs ?: recentProjects.activeProject()
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
            val port = OSCPortOut.DEFAULT_SC_LANG_OSC_PORT + Random.nextInt(1, 10)
            val client = OSCSuperColliderClient.create(context, port)
            client.consoleMonitor.addListener(ConsoleMonitor.PipeToSystemOut)
            client.onClientReady {
                Platform.runLater {
                    progressBar.displayProgress(0.1, "SuperCollider started, booting server...")
                    try {
                        context[SuperColliderClient] = client
                        context[ActiveObjectsManager] = ActiveObjectsManager(context)
                        client.addListener(context[ActiveObjectsManager])
                        context[NodeTree] = NodeTree(client)
                        context[ScoreObjectScheduler] = ScoreObjectScheduler(context)
                        context[ScoreObjectDuplicator] = ScoreObjectDuplicator()
                        context[Recorder] = Recorder(context)
                        clientReady(client)
                    } catch (e: Exception) {
                        Logger.error("Error while $description", e)
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
                        Logger.error("Error while $description", e)
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
        launchActivity("Show Ponticello Launcher") { LauncherActivity(this) }
    }

    fun closeRequest(automaticallySave: Boolean = false) {
        val project = getActiveProject()
        if (project != null) {
            saveChanges(automaticallySave) ?: return
        }
        currentActivity.hide()
        quitApplication()
    }

    private fun askIfUserWantsToSave() =
        YesNoPrompt("Save project before exiting?", cancellable = true, default = null)
            .showDialog(rootContext)

    fun quitApplication() {
        rootContext[PonticelloFiles].resolve("settings.json").writeJson(rootContext[GlobalSettings])
        exitProcess(0)
    }

    companion object : PublicProperty<PonticelloLauncher> by publicProperty("ponticello-launcher") {
        val currentProject = publicProperty<PonticelloProject>("current-project")

        val rootContext: Context = HextantCore.defaultContext().apply {
            val files = PonticelloFiles(this)
            set(PonticelloFiles, files)
            set(GlobalSettings, files.loadSettings())
            get(GlobalSettings).initialize(this)
            set(
                GlobalDefinitionLibrary.instruments, GlobalDefinitionLibrary(
                    files.resolve("global-instruments"),
                    serializer(), objectType = "Instrument"
                )
            )
            registerImplementationsFromClasspath()
            HextantCore.apply(this, PluginBuilder.Phase.Initialize, null)
            PonticelloHextantPlugin.apply(this, PluginBuilder.Phase.Initialize, null)
            SubWindow.globalStylesheets.addAll(get(Stylesheets).all())
            val midiReceiver = ContextualMidiReceiver()
            midiReceiver.initialize(this)
            midiReceiver.attachTo("Xjam")
            set(ContextualMidiReceiver, midiReceiver)
        }
    }
}