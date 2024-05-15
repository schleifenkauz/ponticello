package xenakis.ui

import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.core.HextantCore
import hextant.plugins.PluginBuilder
import javafx.application.Platform
import javafx.stage.FileChooser
import javafx.stage.Stage
import xenakis.impl.UDPSuperColliderClient
import xenakis.impl.registerImplementationsFromClasspath
import xenakis.model.XenakisProject
import java.io.File
import java.util.prefs.Preferences
import kotlin.concurrent.thread

class XenakisController(private val primaryStage: Stage) {
    private val listeners = mutableListOf<XenakisListener>()

    private fun listeners(action: XenakisListener.() -> Unit) {
        listeners.forEach(action)
    }

    fun addListener(listener: XenakisListener) {
        listeners.add(listener)
    }

    private val prefs = Preferences.userNodeForPackage(XenakisApp::class.java)

    private val recentProjects = loadRecentProjects()

    lateinit var context: Context
        private set

    lateinit var client: UDPSuperColliderClient
        private set

    private var _currentProject: XenakisProject? = null

    var currentProject: XenakisProject
        get() = _currentProject ?: error("no project opened")
        private set(project) {
            _currentProject = project
            context[XenakisController.currentProject] = project
            prefs.put("lastFile", project.projectFile.absolutePath)
            addRecentProject(project.projectFile)
            listeners { displayProject(currentProject) }
        }

    val isProjectOpened get() = _currentProject != null

    var isSuperColliderReady: Boolean = false
        private set

    private val fc = FileChooser().apply {
        extensionFilters.setAll(
            FileChooser.ExtensionFilter("JSON Files", "*.json"),
            FileChooser.ExtensionFilter("SuperCollider Scripts", "*.scd"),
            FileChooser.ExtensionFilter("Sound Files", listOf("*.wav", "*.mp3"))
        )
        initialDirectory = File(System.getProperty("user.home")).resolve(".xenakis")
    }

    fun setupHextant() {
        context = HextantCore.defaultContext()
        context[XenakisApp.primaryStage] = primaryStage
        context.registerImplementationsFromClasspath()
        HextantCore.apply(context, PluginBuilder.Phase.Initialize, null)
        XenakisHextantPlugin.apply(context, PluginBuilder.Phase.Initialize, null)
    }

    fun startSuperCollider() {
        thread(name = "SuperCollider startup thread", isDaemon = true) {
            client = UDPSuperColliderClient.create()
            context[UDPSuperColliderClient] = client
            client.addStatusListener { status ->
                if (status == UDPSuperColliderClient.Status.Listening) {
                    isSuperColliderReady = true
                    Platform.runLater {
                        listeners { superColliderReady() }
                    }
                }
            }
        }
    }

    fun restartScSynth() {
        client.postAsync("s.reboot;")
    }

    fun showServerWindow() {
        client.postAsync("s.makeWindow;")
    }

    fun startXenakis() {
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
        setExtensionFilter("*.json")
        val file = lastFile() ?: fc.showSaveDialog(primaryStage) ?: return
        tryWithAlert("Saving score") {
            saveAs(file)
        }
    }

    private fun saveAs(file: File) {
        currentProject.saveTo(file)
        prefs.put("lastFile", file.absolutePath)
        addRecentProject(file)
    }

    fun openProject() {
        setExtensionFilter("*.json")
        val file = fc.showOpenDialog(primaryStage) ?: return
        openProject(file)
    }

    fun openProject(file: File): Boolean {
        tryWithAlert("Opening project") {
            val project = XenakisProject.loadFrom(file, context)
            currentProject = project
        } ?: return false
        return true
    }

    private fun setExtensionFilter(ext: String) {
        fc.selectedExtensionFilter = fc.extensionFilters.find { it.extensions.contains(ext) }
    }

    fun createNewProject() {
        val location = showSaveDialog("*.json") ?: return
        currentProject = XenakisProject.create(location, context)
        saveAs(location)
    }

    fun showOpenDialog(extension: String): File? {
        setExtensionFilter(extension)
        return fc.showOpenDialog(primaryStage)
    }
    fun showSaveDialog(extension: String): File? {
        setExtensionFilter(extension)
        check(fc.initialDirectory.isDirectory)
        return fc.showSaveDialog(primaryStage)
    }

    private fun clearLastFile() {
        prefs.put("lastFile", "")
    }

    fun exportAsScript() {
        setExtensionFilter("*.scd")
        val file = fc.showSaveDialog(primaryStage) ?: return
        tryWithAlert("Exporting score as SuperCollider script") {
            val writer = file.writer()
            currentProject.exportAsScript(writer)
            writer.close()
        }
    }

    fun closeProject() {
        clearLastFile()
        goToStartupScreen()
    }

    private fun goToStartupScreen() {
        listeners { displayStartupScreen() }
    }

    fun addTime() {
        val amount = showDoubleInputDialog("How much time to add", context, 0.0..1000.0, 10.0) ?: return
        val score = currentProject.score
        score.addTime(score.totalDuration, amount)
    }

    fun quitApplication() {
        client.quit()
    }

    companion object {
        val currentProject = publicProperty<XenakisProject>("currentProject", null)
    }
}