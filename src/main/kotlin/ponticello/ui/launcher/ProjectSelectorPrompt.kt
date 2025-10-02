package ponticello.ui.launcher

import fxutils.prompt.SelectorPrompt
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.Region
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.model.project.PonticelloProject

class ProjectSelectorPrompt(
    private val launcher: PonticelloLauncher
) : SelectorPrompt<OpenProjectOption>("Project...") {
    override fun options(): List<OpenProjectOption> {
        val recent = launcher.recentProjects.list().map { dir ->
            val name = PonticelloProject.getName(dir)
            OpenProjectOption.RecentProject(dir, name)
        }
        val otherOptions = listOf(
            OpenProjectOption.CreateNew,
            OpenProjectOption.OpenFromFileSystem,
            OpenProjectOption.CloneFromRepository,
            OpenProjectOption.CloseProject
        )
        return otherOptions + recent
    }

    override fun extractText(option: OpenProjectOption): String = when (option) {
        OpenProjectOption.CreateNew -> "create new"
        OpenProjectOption.CloneFromRepository -> "clone"
        OpenProjectOption.OpenFromFileSystem -> "open"
        OpenProjectOption.CloseProject -> "close"
        is OpenProjectOption.RecentProject -> option.name
    }

    override fun createCell(option: OpenProjectOption): Region {
        val text = when (option) {
            OpenProjectOption.CloneFromRepository -> "Clone repository..."
            OpenProjectOption.CreateNew -> "Create new..."
            OpenProjectOption.OpenFromFileSystem -> "Open..."
            OpenProjectOption.CloseProject -> "Close project"
            is OpenProjectOption.RecentProject -> option.name
        }
        val icon = when (option) {
            OpenProjectOption.CloneFromRepository -> Codicons.SOURCE_CONTROL
            OpenProjectOption.CreateNew -> MaterialDesignP.PLUS
            OpenProjectOption.OpenFromFileSystem -> Codicons.FOLDER_OPENED
            OpenProjectOption.CloseProject -> MaterialDesignC.CLOSE
            is OpenProjectOption.RecentProject -> Codicons.PLAY
        }
        val label = Label(text, FontIcon(icon))
        label.contentDisplay = ContentDisplay.LEFT
        if (option is OpenProjectOption.RecentProject) {
            label.tooltip = Tooltip(option.directory.absolutePath)
        }
        return label
    }
}