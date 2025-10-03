package ponticello.ui.launcher

import fxutils.controls.CheckBox
import fxutils.prompt.CompoundPrompt
import fxutils.styleClass
import javafx.scene.control.Button
import javafx.scene.control.TextArea
import javafx.scene.control.ToggleButton
import javafx.scene.layout.BorderPane
import org.controlsfx.control.SegmentedButton
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.javafx.FontIcon
import ponticello.model.project.PonticelloProject
import reaktive.value.now

class CloseProjectDialog(project: PonticelloProject) : CompoundPrompt<CloseProjectDialog.Result>(
    "Save project before closing?", confirmText = "_Yes",
) {
    private val cleanupObjectsOption = CheckBox(false, text = "Remove unused score objects")

    private val commitChangesOption = CheckBox(false, text = "Commit changes")

    private val commitBtn = ToggleButton("Commit", FontIcon(Codicons.GIT_COMMIT))
    private val pushBtn = ToggleButton("Commit & Push", FontIcon(Codicons.REPO_PUSH))

    private val commitMessageArea = TextArea()

    private val dontSaveBtn = Button("_No") styleClass "sleek-button"

    init {
        content.spacing = 5.0

        dontSaveBtn.setOnAction {
            commit(Result(cleanupObjectsOption.isSelected, Action.None))
        }

        commitBtn.selectedProperty().addListener { _, _, selected ->
            commitMessageArea.isManaged = selected || pushBtn.isSelected
            window.sizeToScene()
        }
        pushBtn.selectedProperty().addListener { _, _, selected ->
            commitMessageArea.isManaged = selected || commitBtn.isSelected
            window.sizeToScene()
        }
        content.children.add(cleanupObjectsOption)
        val vc = project.versionControl.now
        if (vc != null) {
            if (vc.getRemoteUrl() != null) {
                content.children.add(BorderPane(SegmentedButton(commitBtn, pushBtn)))
            } else {
                content.children.add(commitChangesOption)
            }
        }
        commitMessageArea.promptText = "Commit message..."
        content.children.add(commitMessageArea)
        commitMessageArea.isManaged = false
    }

    override fun buttons(): List<Button> = listOf(cancelButton, dontSaveBtn, confirmButton)

    override fun confirm(): Result {
        val action = when {
            commitChangesOption.isSelected -> Action.Save
            commitBtn.isSelected -> Action.Commit(commitMessageArea.text, push = false)
            pushBtn.isSelected -> Action.Commit(commitMessageArea.text, push = true)
            else -> Action.None
        }
        return Result(cleanupObjectsOption.isSelected, action)
    }

    data class Result(val cleanupObjects: Boolean, val action: Action)

    sealed class Action {
        data object None : Action()
        data object Save : Action()
        data class Commit(val message: String, val push: Boolean) : Action()
    }
}