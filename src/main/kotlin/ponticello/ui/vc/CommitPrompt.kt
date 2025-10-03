package ponticello.ui.vc

import fxutils.controls.CheckBox
import fxutils.prompt.CompoundPrompt
import fxutils.styleClass
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import ponticello.impl.Logger
import ponticello.model.git.ProjectVersionControl
import ponticello.model.project.*
import reaktive.value.now
import reaktive.value.reactiveVariable

class CommitPrompt(
    private val project: PonticelloProject,
    private val versionControl: ProjectVersionControl,
) : CompoundPrompt<Unit>("Commit", labelWidth = 150.0) {
    private val commitMessageArea = TextArea()
    private val selectionStates = (allComponents - setOf(UI_STATE, OBJECTS)).associateWith { reactiveVariable(true) }
    private val pushOptions = CheckBox(false, "Push to remote")

    init {
        content.children.add(Label("Select components to commit:") styleClass "heading")
        for ((component, selected) in selectionStates) {
            addItem(component.displayName, CheckBox(selected, ""))
        }
        commitMessageArea.promptText = "Commit message..."
        content.children.add(commitMessageArea)
        if (versionControl.hasRemote.now) {
            content.children.add(pushOptions)
        }
    }

    override fun confirm() {
        project.save()
        val selectedComponents = selectionStates.filterValues { selected -> selected.now }.keys.toMutableSet()
        if (SCORE in selectedComponents) {
            selectedComponents.add(OBJECTS)
        }
        if (versionControl.commitChanges(selectedComponents, message = commitMessageArea.text)) {
            Logger.confirm("Commit successful!", Logger.Category.VersionControl)
            if (pushOptions.isSelected) {
                versionControl.pushToRemote(JavaFXGitUserInteraction) { success ->
                    if (success) {
                        Logger.confirm("Pushed to remote.", Logger.Category.VersionControl)
                    } else {
                        Logger.error("Failed to push to remote.", Logger.Category.VersionControl)
                    }
                }
            }
        } else {
            Logger.error("Commit failed!", Logger.Category.VersionControl)
        }
    }
}