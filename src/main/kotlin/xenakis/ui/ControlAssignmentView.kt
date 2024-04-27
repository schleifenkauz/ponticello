package xenakis.ui

import javafx.scene.layout.VBox
import xenakis.model.ParameterControl
import xenakis.model.SynthObject
import xenakis.model.XenakisProject
import xenakis.sc.ParameterizedObject

class ControlAssignmentView(
    parameterizedObject: ParameterizedObject,
    project: XenakisProject,
    private val controls: MutableList<ParameterControl>
) : VBox() {
    private val editors = mutableListOf<ControlAssignmentEditor>()

    init {
        prefWidth = 420.0
        for (control in controls) {
            val param = parameterizedObject.getParameter(control.parameter)
            val editor = ControlAssignmentEditor(param, project)
            editor.setControl(control)
            editors.add(editor)
            children.add(editor)
        }
    }

    fun updateFromUserInput() {
        controls.clear()
        controls.addAll(editors.map { it.createControl() })
    }

    companion object {
        fun show(obj: SynthObject, project: XenakisProject): Boolean {
            val view = ControlAssignmentView(obj.synthDef, project, obj.controls)
            return showDialog(view, project.context) { view.updateFromUserInput() } != null
        }

    }
}