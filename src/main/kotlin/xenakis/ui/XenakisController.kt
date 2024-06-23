package xenakis.ui

import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.HextantCore
import hextant.plugins.PluginBuilder
import hextant.serial.SnapshotAware
import hextant.serial.readJson
import hextant.serial.writeJson
import hextant.undo.UndoManager
import javafx.application.Platform
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import xenakis.impl.ConsoleMonitor
import xenakis.impl.OSCSuperColliderClient
import xenakis.impl.StatusListener.StatusUpdate
import xenakis.impl.SuperColliderClient
import xenakis.impl.registerImplementationsFromClasspath
import xenakis.model.Settings
import xenakis.model.XenakisProject
import java.io.File
import java.util.prefs.Preferences

class XenakisController(private val primaryStage: Stage) {
    private val listeners = mutableListOf<XenakisListener>()

    private fun listeners(action: XenakisListener.() -> Unit) {
        Platform.runLater {
            listeners.forEach(action)
        }
    }

    fun addListener(listener: XenakisListener) {
        listeners.add(listener)
    }

    private val prefs = Preferences.userNodeForPackage(XenakisApp::class.java)

    private val recentProjects = loadRecentProjects()

    lateinit var context: Context
        private set

    lateinit var client: SuperColliderClient
        private set

    private var _currentProject: XenakisProject? = null

    var currentProject: XenakisProject
        get() = _currentProject ?: error("no project opened")
        private set(project) {
            _currentProject = project
            context[UndoManager].reset()
            context[XenakisController.currentProject] = project
            prefs.put("lastFile", project.projectDirectory.absolutePath)
            addRecentProject(project.projectDirectory)
            listeners { displayProject(currentProject) }
        }

    val isProjectOpened get() = _currentProject != null

    var isSuperColliderReady: Boolean = false
        private set

    private val userHome = File(System.getProperty("user.home"))

    private val xenakisDir = userHome.resolve(".xenakis").also { dir -> if (!dir.exists()) dir.mkdir() }

    private val fc = FileChooser().apply {
        extensionFilters.setAll(
            FileChooser.ExtensionFilter("Xenakis Projects", "*.xen"),
            FileChooser.ExtensionFilter("SuperCollider Scripts", "*.scd"),
            FileChooser.ExtensionFilter("Sound Files", listOf("*.wav", "*.mp3"))
        )
        initialDirectory = userHome
    }

    private val dc = DirectoryChooser().apply {
        initialDirectory = userHome.resolve("Xenakis Projects").also { dir -> if (!dir.exists()) dir.mkdir() }
    }

    fun setupHextant() {
        setProgress(0.0, "Setting up Hextant")
        context = HextantCore.defaultContext()
        SnapshotAware.Serializer.reconstructionContext = context
        context[XenakisApp.primaryStage] = primaryStage
        context[Settings] = loadSettings()
        context.registerImplementationsFromClasspath()
        HextantCore.apply(context, PluginBuilder.Phase.Initialize, null)
        XenakisHextantPlugin.apply(context, PluginBuilder.Phase.Initialize, null)
        setProgress(0.1, "OSC connected")
    }

    private fun loadSettings(): Settings {
        val file = xenakisDir.resolve("settings.json")
        return if (file.exists()) context.withoutUndo { file.readJson<Settings>() }
        else Settings.createDefault()
    }

    fun startSuperCollider() {
        client = OSCSuperColliderClient.create()
        client.consoleMonitor.addListener(ConsoleMonitor.PipeToSystemOut)
        context[SuperColliderClient] = client
        setProgress(0.1, "Starting SuperCollider")
        client.statusListener.on(StatusUpdate.ReadyToBoot) {
            isSuperColliderReady = false
            Thread.sleep(200)
            client.run("s.reboot;")
            setProgress(0.2, "Booting scsynth")
            Platform.runLater {
                listeners { waitingForBoot() }
            }
        }
        client.statusListener.on(StatusUpdate.ServerBooted) {
            isSuperColliderReady = true
            setProgress(0.3, "Connecting via OSC")
            Platform.runLater {
                listeners { readyToPlay() }
            }
        }
        client.statusListener.on(StatusUpdate.OSCReady) {
            Thread.sleep(200)
            setProgress(0.4, "OSC connected")
            startXenakis()
        }
    }

    fun restartScSynth() {
        client.run("s.reboot;")
    }

    private fun startXenakis() {
        val file = lastFile()
        if (file != null) {
            if (!file.exists() || !openProject(file)) {
                clearLastFile()
                goToStartupScreen()
            }
        } else {
            goToStartupScreen()
        }
    }

    private fun loadRecentProjects(): MutableList<File> {
        val str = prefs.get("recentProjects", "")
        val paths = str.split(File.pathSeparator).filter { it.isNotEmpty() }
        return paths.mapTo(mutableListOf(), ::File)
    }

    private fun addRecentProject(path: File) = updateRecentProjects {
        recentProjects.remove(path)
        recentProjects.add(0, path)
    }

    fun removeFromRecentProjects(proj: File) = updateRecentProjects {
        recentProjects.remove(proj)
    }

    private inline fun updateRecentProjects(action: MutableList<File>.() -> Unit) {
        recentProjects.action()
        val str = recentProjects.joinToString(File.pathSeparator)
        prefs.put("recentProjects", str)
    }

    fun recentProjects(): List<File> = recentProjects

    private fun lastFile(): File? = prefs.get("lastFile", null)?.let(::File)?.takeIf { it.exists() }

    fun saveProject() {
        val file = lastFile() ?: dc.showDialog(primaryStage) ?: return
        tryWithAlert("Saving score") { saveIn(file) }
        notifyInfo("Saved project ${currentProject.projectDirectory.name}")
    }

    private fun saveIn(folder: File) {
        currentProject.saveTo(folder)
        prefs.put("lastFile", folder.absolutePath)
        addRecentProject(folder)
    }

    fun openProject() {
        setExtensionFilter("*.xen")
        val file = fc.showOpenDialog(primaryStage) ?: return
        openProject(file.parentFile)
    }

    fun openProject(folder: File): Boolean {
        tryWithAlert("Opening project") {
            val project = XenakisProject.loadFrom(folder, context, this)
            currentProject = project
        } ?: return false
        return true
    }

    fun setProgress(progress: Double, status: String) {
        listeners { displayProgress(progress, status) }
    }

    private fun setExtensionFilter(ext: String) {
        fc.selectedExtensionFilter = fc.extensionFilters.find { it.extensions.contains(ext) }
    }

    fun createNewProject() {
        showTextPrompt("Project name", "", context) { name ->
            if (name.isNotBlank()) {
                val location = userHome.resolve("Xenakis Projects").resolve(name)
                location.mkdir()
                location.resolve("project.xen").writeText(location.name)
                currentProject = XenakisProject.create(location, context)
                saveIn(location)
                true
            } else false
        }
    }

    fun showOpenDialog(extension: String): File? {
        setExtensionFilter(extension)
        return fc.showOpenDialog(primaryStage)
    }

    private fun clearLastFile() {
        prefs.put("lastFile", "")
    }

    fun closeProject() {
        clearLastFile()
        goToStartupScreen()
    }

    private fun goToStartupScreen() {
        _currentProject = null
        listeners { displayStartupScreen() }
    }

    fun quitApplication() {
        xenakisDir.resolve("settings.json").writeJson(context[Settings])
        client.quit()
    }

    companion object {
        val currentProject = publicProperty<XenakisProject>("currentProject", null)
    }
}