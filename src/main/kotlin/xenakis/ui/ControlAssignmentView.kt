package xenakis.ui

import javafx.scene.layout.VBox
import xenakis.model.ParameterControl
import xenakis.model.ScoreObject
import xenakis.model.SynthObject
import xenakis.model.XenakisProject
import xenakis.sc.ParameterizedObject

class ControlAssignmentView(
    private val obj: ScoreObject,
    controls: List<ParameterControl>,
    parameterizedObject: ParameterizedObject,
    project: XenakisProject
) : VBox() {
    private val editors = mutableListOf<ControlAssignmentEditor>()

    init {
        prefWidth = 500.0
        for (control in controls) {
            val param = parameterizedObject.getParameter(control.parameter)
            val editor = ControlAssignmentEditor(param, project)
            editor.setControl(control)
            editors.add(editor)
            children.add(editor)
        }
    }

    fun updateFromUserInput() {
        obj.controls = editors.map { ed -> ed.createControl() }
    }

    companion object {
        fun show(obj: SynthObject, controls: List<ParameterControl>, project: XenakisProject): Boolean {
            val view = ControlAssignmentView(obj, controls, obj.synthDef, project)
            return view.showDialog("Configure controls",
                applyStylesheets = { scene -> scene.stylesheets.add("xenakis/ui/style.css") },
                resultConverter = { view.updateFromUserInput() }
            ) != null
        }

    }
}