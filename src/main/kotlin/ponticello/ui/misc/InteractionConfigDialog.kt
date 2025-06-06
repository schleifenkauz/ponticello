package ponticello.ui.misc

import fxutils.prompt.CompoundPrompt
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import fxutils.undo.compoundEdit
import javafx.scene.control.CheckBox
import ponticello.model.project.UIState
import reaktive.value.now

class InteractionConfigDialog(
    private val config: UIState,
) : CompoundPrompt<Unit>("Interaction config", labelWidth = 140.0) {
    private val askForGroupNamesBox = CheckBox() named "Ask for group names"
    private val askForCloneNames = CheckBox() named "Ask for clone names"

    init {
        askForCloneNames.isSelected = config.askForCloneNames.now
        askForGroupNamesBox.isSelected = config.askForGroupNames.now
    }

    override fun confirm() {
        val manager = config.context[UndoManager]
        manager.compoundEdit("Update interaction config") {
            VariableEdit.updateVariable(config.askForCloneNames, askForCloneNames.isSelected, manager)
            VariableEdit.updateVariable(config.askForGroupNames, askForGroupNamesBox.isSelected, manager)
        }
    }
}