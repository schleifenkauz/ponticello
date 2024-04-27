package xenakis.ui

import bundles.set
import hextant.context.Context
import hextant.core.HextantCore
import hextant.plugins.PluginBuilder
import javafx.stage.FileChooser
import javafx.stage.Stage
import xenakis.impl.UDPSuperColliderClient
import xenakis.impl.registerImplementationsFromClasspath
import xenakis.model.AudioFlowGraph
import xenakis.model.Score
import xenakis.model.XenakisProject
import xenakis.sc.SynthDef
import java.io.File
import java.util.prefs.Preferences
import kotlin.system.exitProcess

class XenakisController(private val primaryStage: Stage) {
    private val listeners = mutableListOf<XenakisListener>()

    private fun listeners(action: XenakisListener.() -> Unit) {
        listeners.forEach(action)
    }

    fun addListener(listener: XenakisListener) {
        listeners.add(listener)
    }

    private val prefs = Preferences.userNodeForPackage(XenakisApp::class.java)

    lateinit var context: Context
        private set

    lateinit var client: UDPSuperColliderClient
        private set

    lateinit var currentProject: XenakisProject
        private set

    private val fc = FileChooser().apply {
        extensionFilters.setAll(
            FileChooser.ExtensionFilter("JSON Files", "*.json"),
            FileChooser.ExtensionFilter("SuperCollider Scripts", "*.scd")
        )
        initialDirectory = File("C:\\Users\\nikok\\Music\\frost")
    }

    fun setupHextant() {
        context = HextantCore.defaultContext()
        context.registerImplementationsFromClasspath()
        HextantCore.apply(context, PluginBuilder.Phase.Initialize, null)
        XenakisHextantPlugin.apply(context, PluginBuilder.Phase.Initialize, null)
    }

    fun startSuperCollider() {
        client = UDPSuperColliderClient.create()
        client.post("s.options.numWireBufs = 512;")
        client.post("s.boot;")
        context[UDPSuperColliderClient] = client
    }

    private fun setCurrentProject(project: XenakisProject) {
        currentProject = project
        listeners { displayProject(currentProject) }
    }

    fun restartSuperCollider() {
        client.post("s.reboot;")
    }

    fun showServerWindow() {
        client.post("s.makeWindow;")
    }

    fun loadOrCreateProject() {
        val file = lastFile()
        val project = if (file != null) {
            if (!file.exists()) {
                alertError("Recent project file $file was either moved or deleted.")
                clearLastFile()
                makeNewProject() ?: exitProcess(0)
            }
            val fromFile = tryWithAlert("Opening project") { XenakisProject.loadFrom(file, context) }
            if (fromFile == null) {
                clearLastFile()
                makeNewProject() ?: exitProcess(0)
            } else fromFile
        } else makeNewProject() ?: exitProcess(0)
        project.context = context
        setCurrentProject(project)
    }

    private fun makeNewProject(): XenakisProject? {
        val totalDuration = showDoubleInputDialog(
            title = "Total duration (seconds)", context,
            range = 1.0..1000.0,
            initialValue = 60.0
        ) ?: return null
        return XenakisProject(
            globalVariables = mutableMapOf(),
            synthDefs = mutableListOf(SynthDef.default),
            flowGraph = AudioFlowGraph.createDefault(),
            buffers = mutableListOf(),
            score = Score(totalDuration)
        )
    }

    private fun lastFile(): File? = prefs.get("lastFile", null)?.let(::File)?.takeIf { it.exists() }

    fun saveProject() {
        setExtensionFilter("*.json")
        val file = lastFile() ?: fc.showSaveDialog(primaryStage) ?: return
        tryWithAlert("Saving score") {
            currentProject.saveTo(file)
            prefs.put("lastFile", file.absolutePath)
        }
    }

    fun openProject() {
        setExtensionFilter("*.json")
        val file = fc.showOpenDialog(primaryStage) ?: return
        tryWithAlert("Opening score") {
            val project = XenakisProject.loadFrom(file, context)
            setCurrentProject(project)
            prefs.put("lastFile", file.absolutePath)
        }
    }

    private fun setExtensionFilter(ext: String) {
        fc.selectedExtensionFilter = fc.extensionFilters.find { it.extensions.contains(ext) }
    }

    fun createNewProject() {
        val project = makeNewProject() ?: return
        setCurrentProject(project)
        clearLastFile()
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
        }
    }

    fun addTime() {
        val amount = showDoubleInputDialog("How much time to add", context, 0.0..1000.0, 10.0) ?: return
        val score = currentProject.score
        score.addTime(score.totalDuration, amount)
    }
}