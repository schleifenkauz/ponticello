package xenakis.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.readJson
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import xenakis.impl.Logger
import xenakis.model.Settings
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import java.io.File

class XenakisFiles(private val context: Context) {
    private val userHome = File(System.getProperty("user.home"))

    private val ponticelloDir = run {
        val defaultHome = userHome.resolve(".ponticello")
        try {
            val ponticelloHome = System.getenv("PONTICELLO_HOME")?.let(::File)
            ponticelloHome ?: defaultHome
        } catch (e: Exception) {
            Logger.error("Failed to resolve Ponticello home directory: ${e.message}, using default location.", e)
            defaultHome
        }
    }

    val projectsDir = run {
        val defaultProjectsDir = userHome.resolve("PonticelloProjects")
        try {
            val projectsHome = System.getenv("PONTICELLO_PROJECTS")?.let(::File)
            projectsHome ?: defaultProjectsDir
        } catch (e: Exception) {
            Logger.error("Failed to resolve Projects directory: ${e.message}, using default location", e)
            defaultProjectsDir
        }
    }

    fun resolve(vararg path: String): File = ponticelloDir.resolve(path.joinToString("/"))

    private val fc = FileChooser().apply {
        extensionFilters.setAll(
            FileChooser.ExtensionFilter("Xenakis Projects", "*.xen"),
            FileChooser.ExtensionFilter("SuperCollider Scripts", "*.scd"),
            FileChooser.ExtensionFilter("Sound Files", listOf("*.wav", "*.mp3"))
        )
        initialDirectory = projectsDir
    }

    private val dc = DirectoryChooser()

    private fun setExtensionFilter(ext: String) {
        fc.selectedExtensionFilter = fc.extensionFilters.find { it.extensions.contains(ext) }
    }

    fun showOpenDialog(extension: String): File? {
        setExtensionFilter(extension)
        return fc.showOpenDialog(context[primaryStage])
    }

    fun loadSettings(): Settings {
        val file = ponticelloDir.resolve("settings.json")
        return if (file.exists()) context.withoutUndo { file.readJson<Settings>() }
        else Settings.createDefault()
    }

    companion object : PublicProperty<XenakisFiles> by publicProperty("XenakisFiles")
}