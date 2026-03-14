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
import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import ponticello.impl.Logger
import ponticello.impl.json
import ponticello.impl.registerImplementationsFromClasspath
import ponticello.model.GlobalSettings
import ponticello.model.ServerOptions
import ponticello.model.code.ScriptObject
import ponticello.model.git.ProjectGitRepository
import ponticello.model.instr.GlobalDefinitionLibrary
import ponticello.model.obj.project
import ponticello.model.player.Recorder
import ponticello.model.player.ScoreObjectScheduler
import ponticello.model.player.ScorePlayer
import ponticello.model.project.*
import ponticello.model.project.PonticelloProject.Companion.projectDirectory
import ponticello.model.tree.AudioNodeTree
import ponticello.sc.client.ConsoleMonitor
import ponticello.sc.client.DummySuperColliderClient
import ponticello.sc.client.OSCSuperColliderClient
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.showDialog
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.midi.ContextualMidiReceiver
import ponticello.ui.midi.MidiRecorder
import ponticello.ui.score.ScoreObjectDuplicator
import ponticello.ui.vc.JavaFXGitUserInteraction
import reaktive.Observer
import reaktive.value.now
import java.io.File
import java.net.URL
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.system.exitProcess

class PonticelloLauncher {
    val recentProjects = RecentProjects()
    val rootContext get() = PonticelloLauncher.rootContext

    private lateinit var applicationParameters: Application.Parameters

    init {
        rootContext[PonticelloLauncher] = this
    }

    lateinit var currentActivity: Activity
        private set

    private fun <A : Activity> launchActivity(description: String, createActivity: () -> A): A? {
        currentActivity.hide()
        val stage = Stage()
        stage.setOnCloseRequest { quitPonticello() }
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
        if (!confirmCloseRequest()) return
        val file = rootContext[PonticelloFiles].showOpenDialog("*.pont") ?: return
        openProject(file.parentFile, askSync = true)
    }

    private fun confirmCloseRequest(autoSave: Boolean = false): Boolean {
        val project = getActiveProject() ?: return true
        project.context[AppLayout].saveLayoutState()
        project[UI_STATE].saveWindowStates()
        project.save(UI_STATE)
        if (autoSave) {
            val ok = save(rootContext.project)
            return ok
        }
        if (!project.context[UndoManager].hasUnsavedChanges.now) return true
        val result = CloseProjectDialog(project).showDialog(rootContext) ?: return false
        if (result.cleanupObjects) {
            project.objects.removeUnusedObjects()
        }
        if (result.action != CloseProjectDialog.Action.None) {
            val ok = save(rootContext.project)
            if (!ok) return false
        }
        if (result.action is CloseProjectDialog.Action.Commit) {
            val vc = project.versionControl.now!!
            vc.commitChanges((allComponents - UI_STATE).toSet(), result.action.message)
            if (result.action.push) {
                vc.pushToRemote(JavaFXGitUserInteraction) {}
            }
        }
        return true
    }

    fun openProject(folder: File, askSync: Boolean) {
        maybeSyncRepository(folder, askSync) {
            val context = createProjectContext()
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
    }

    private fun maybeSyncRepository(directory: File, askSync: Boolean, afterwards: () -> Unit) {
        if (!askSync) return afterwards()
        val repo = ProjectGitRepository.get(directory) ?: return afterwards()
        if (!repo.hasRemote.now) return afterwards()
        val sync = YesNoPrompt("Sync repository before opening?").showDialog() ?: return
        if (sync) {
            repo.pullFromRemote(JavaFXGitUserInteraction) {
                Platform.runLater(afterwards)
            }
        } else {
            afterwards()
        }
    }

    private fun getOrLaunchLoadingScreen() =
        currentActivity as? LoadingScreen ?: launchActivity("Showing loading screen") { LoadingScreen(rootContext) }!!

    private fun getActiveProject(): PonticelloProject? = (currentActivity as? PonticelloMainActivity)?.project

    private fun openProject(project: PonticelloProject) {
        ScorePlayer.clearInstances()
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
        if (!confirmCloseRequest(autoSave)) return
        recentProjects.clearActiveProject()
        showLauncher()
    }

    fun createNewProject() {
        if (!confirmCloseRequest()) return
        val name = PredicateTextPrompt("Project name", "") { name -> name.isNotBlank() }
            .showDialog(rootContext) ?: return
        val location = PonticelloFiles.projectsDir.resolve(name)
        location.mkdirs()
        location.resolve("project.pont").writeText(location.name)
        createNewProject(location)
    }

    private fun createNewProject(location: File) {
        val context = createProjectContext()
        val progressBar = getOrLaunchLoadingScreen()
        setupSuperCollider(
            context,
            "creating new project",
            clientReady = { client -> client.run("s.boot") },
            serverReady = {
                progressBar.displayProgress(0.2, "Booted SuperCollider server, creating new project...")
                val project = PonticelloProject.create(location, context)
                project.initialize(context, progressBar, totalDeltaProgress = 0.7)
                save(project)
                progressBar.displayProgress(0.95, "Created new project...")
                openProject(project)
            })
    }

    private fun createProjectContext(): Context {
        val context = rootContext.extend()
        context[UndoManager] = UndoManager.newInstance()
        return context
    }

    fun cloneRepository() {
        val prompt = PredicateTextPrompt("Git repository URL", "") { url -> url.isNotBlank() }
        prompt.content.prefWidth = 300.0
        val url = prompt.showDialog(rootContext) ?: return
        cloneRepository(url)
    }

    private fun cloneRepository(url: String) {
        val projectName = url.substringAfterLast('/').substringBeforeLast('.')
        val location = PonticelloFiles.projectsDir.resolve(projectName)
        Logger.confirm("Cloning $url into $location, this may take a while.", Logger.Category.VersionControl)
        scope.launch {
            val credentials = ProjectGitRepository.getCredentials(JavaFXGitUserInteraction)
            try {
                Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(location)
                    .setGitDir(location.resolve(".git"))
                    .setCredentialsProvider(credentials)
                    .setProgressMonitor(JavaFXGitUserInteraction)
                    .call()
            } catch (e: GitAPIException) {
                Logger.error("Error cloning repository: $url", e)
                Platform.runLater {
                    showLauncher()
                }
                return@launch
            }
            Platform.runLater {
                openProject(location, askSync = false)
            }
        }
    }

    private fun save(project: PonticelloProject): Boolean {
        val ok = project.save()
        recentProjects.push(project.projectDirectory)
        return ok
    }

    fun launchPonticello(primaryStage: Stage, parameters: Application.Parameters) {
        applicationParameters = parameters
        val projectRef = parameters.unnamed.firstOrNull { arg -> !arg.startsWith("-") }
        primaryStage.setOnCloseRequest { quitPonticello() }
        currentActivity = LoadingScreen(rootContext)
        currentActivity.show(primaryStage)
        val projectLocation = when {
            projectRef == null -> null
            projectRef.startsWith("https://") -> try {
                URL(projectRef)
            } catch (e: Exception) {
                Logger.error("Invalid URL $projectRef.")
                null
            }

            projectRef.startsWith("~/") -> PonticelloFiles.userHome.resolve(projectRef.drop(2))
            projectRef.contains('/') -> File(projectRef)
            else -> PonticelloFiles.projectsDir.resolve(projectRef).takeIf { f -> f.isDirectory }
        }
        when (projectLocation) {
            is File -> {
                when {
                    !projectLocation.isDirectory -> {
                        Logger.error("Ponticello project not found. $projectLocation is not a valid directory.")
                        showLauncher()
                    }

                    !projectLocation.resolve("project.pont").isFile -> {
                        Logger.error("$projectLocation does not contains a Ponticello project. (Missing file project.pont)")
                        showLauncher()
                    }

                    else -> {
                        openProject(projectLocation, askSync = true)
                    }
                }
            }

            is URL -> {
                cloneRepository(projectRef!!)
            }
            else -> {
                val recentProject = recentProjects.activeProject()
                if (recentProject != null) openProject(recentProject, askSync = true)
                else showLauncher()
            }
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
            val client = when {
                applicationParameters.unnamed.contains("--dummy-client") ->
                    DummySuperColliderClient(context, sampleRate = 44100.0)

                else -> OSCSuperColliderClient.create(context, port)
            }
            client.consoleMonitor.addListener(ConsoleMonitor.PipeToSystemOut)
            client.onClientReady {
                Platform.runLater {
                    progressBar.displayProgress(0.1, "SuperCollider started, booting server...")
                    try {
                        context[SuperColliderClient] = client
                        AudioNodeTree().initialize(context)
                        context[MidiRecorder] = MidiRecorder(context)
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

    fun quitPonticello(autoSave: Boolean = false) {
        if (!confirmCloseRequest(autoSave)) return
        currentActivity.hide()
        quitApplication()
    }

    private fun askIfUserWantsToSave() =
        YesNoPrompt("Save project before exiting?", cancellable = true, default = null)
            .showDialog(rootContext) //TODO also ask whether to commit/push

    fun quitApplication() {
        rootContext[PonticelloFiles].resolve("settings.json").writeJson(rootContext[GlobalSettings])
        exitProcess(0)
    }

    companion object : PublicProperty<PonticelloLauncher> by publicProperty("ponticello-launcher") {
        private val scope = CoroutineScope(Dispatchers.IO)

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