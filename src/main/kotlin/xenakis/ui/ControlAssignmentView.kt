package xenakis.ui

import hextant.undo.UndoManager
import javafx.scene.layout.VBox
import xenakis.model.SynthObject
import xenakis.model.XenakisProject

class ControlAssignmentView(
    private val obj: SynthObject,
    project: XenakisProject
) : VBox() {
    private val editors = mutableListOf<ControlAssignmentEditor>()

    init {
        prefWidth = 500.0
        for ((parameter, control) in obj.controls) {
            val spec = obj.getSpec(parameter)
            val editor = ControlAssignmentEditor(obj, parameter, spec, project)
            editor.setControl(control)
            editors.add(editor)
            children.add(editor)
        }
    }

    fun updateFromUserInput() {
        obj.context[UndoManager].beginCompoundEdit("Reassign controls")
        for (editor in editors) {
            obj.reassignControl(editor.parameter, editor.getControl())
        }
        obj.context[UndoManager].finishCompoundEdit("Reassign controls")
    }

    companion object {
        fun show(obj: SynthObject, project: XenakisProject): Boolean {
            val view = ControlAssignmentView(obj, project)
            return view.showDialog("Configure controls",
                applyStylesheets = { scene -> scene.stylesheets.add("xenakis/ui/style.css") },
                resultConverter = { view.updateFromUserInput() }
            ) != null
        }

    }
}