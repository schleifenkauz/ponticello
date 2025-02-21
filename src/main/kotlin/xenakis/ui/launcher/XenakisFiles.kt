package xenakis.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.readJson
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Window
import xenakis.model.Settings
import xenakis.ui.XenakisApp.Companion.primaryStage
import java.io.File

class XenakisFiles(private val context: Context) {
    val userHome = File(System.getProperty("user.home"))

    val xenakisDir = userHome.resolve(".xenakis").also { dir -> if (!dir.exists()) dir.mkdir() }

    fun resolve(vararg path: String): File = xenakisDir.resolve(path.joinToString("/"))

    private val fc = FileChooser().apply {
        extensionFilters.setAll(
            FileChooser.ExtensionFilter("Xenakis Projects", "*.xen"),
            FileChooser.ExtensionFilter("SuperCollider Scripts", "*.scd"),
            FileChooser.ExtensionFilter("Sound Files", listOf("*.wav", "*.mp3"))
        )
        initialDirectory = userHome
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
        val file = xenakisDir.resolve("settings.json")
        return if (file.exists()) context.withoutUndo { file.readJson<Settings>() }
        else Settings.createDefault()
    }

    companion object: PublicProperty<XenakisFiles> by publicProperty("XenakisFiles")
}