package xenakis.ui.launcher

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
import reaktive.Observer
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.impl.registerImplementationsFromClasspath
import xenakis.model.ScriptObject
import xenakis.model.ServerOptions
import xenakis.model.Settings
import xenakis.model.flow.NodeTree
import xenakis.model.player.ActiveObjectsManager
import xenakis.model.player.Recorder
import xenakis.model.player.ScoreObjectScheduler
import xenakis.model.player.ScorePlayer
import xenakis.model.project.SERVER_OPTIONS
import xenakis.model.project.XenakisProject
import xenakis.model.project.XenakisProject.Companion.projectDirectory
import xenakis.model.project.get
import xenakis.model.registry.GlobalDefinitionLibrary
import xenakis.sc.client.ConsoleMonitor
import xenakis.sc.client.OSCSuperColliderClient
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.impl.showDialog
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.midi.ContextualMidiReceiver
import xenakis.ui.score.ScoreObjectDuplicator
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
        if (saveChanges() == null) return
        val file = rootContext[XenakisFiles].showOpenDialog("*.xen") ?: return
        openProject(file.parentFile)
    }

    private fun saveChanges(autoSave: Boolean = false): Boolean? {
        val project = getActiveProject() ?: return false
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
                val beforeBootFile = folder.resolve("xenakis_data").resolve("before_boot.json")
                if (beforeBootFile.exists()) {
                    val beforeBoot = beforeBootFile
                        .readJson(ScriptObject.Serializer(ScriptObject.Type.BEFORE_BOOT))
                    beforeBoot.initialize(context)
                    beforeBoot.executeContents(client)
                }
                val serverOptionsFile = folder.resolve("xenakis_data").resolve("server_options.json")
                if (serverOptionsFile.exists()) {
                    val serverOptions = serverOptionsFile
                        .readJson<ServerOptions>()
                    serverOptions.reboot(client)
                }
            },
            serverReady = {
                progressBar.displayProgress(0.2, "Booted SuperCollider server, opening project...")
                thread(isDaemon = true) {
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
        ScorePlayer.clearInstances()
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
        saveChanges(autoSave) ?: return
        recentProjects.clearActiveProject()
        showLauncher()
    }

    fun createNewProject() {
        saveChanges() ?: return
        val name = PredicateTextPrompt("Project name", "") { name -> name.isNotBlank() }
            .showDialog(rootContext) ?: return
        val location = rootContext[XenakisFiles].projectsDir.resolve(name)
        location.mkdir()
        location.resolve("project.xen").writeText(location.name)
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
                val project = XenakisProject.create(location, context)
                save(project, location)
                progressBar.displayProgress(0.3, "Created new project...")
                openProject(project)
            })
    }

    fun saveProject(): Boolean {
        val project = rootContext[currentProject]
        val file = recentProjects.activeProject() ?: rootContext[XenakisFiles].showOpenDialog("*.xen") ?: return false
        val ok = save(project, file)
        if (ok) {
            Logger.confirm("Saved project ${project.projectDirectory.name}", Logger.Category.Project)
        } else {
            Logger.error("Failed to save project ${project.projectDirectory.name}", Logger.Category.Project)
        }
        return ok
    }

    private fun save(project: XenakisProject, folder: File): Boolean {
        val ok = project.saveTo(folder)
        recentProjects.push(folder)
        return ok
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
            val port = OSCPortOut.DEFAULT_SC_LANG_OSC_PORT + 6
            val client = OSCSuperColliderClient.create(port)
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
        launchActivity("Show Xenakis launcher") { LauncherActivity(this) }
    }

    fun closeRequest(automaticallySave: Boolean = false) {
        val project = getActiveProject()
        if (project == null) {
            currentActivity.hide()
            return
        }
        saveChanges(automaticallySave) ?: return
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