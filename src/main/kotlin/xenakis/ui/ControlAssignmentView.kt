package xenakis.ui

import javafx.scene.layout.VBox
import xenakis.model.ParameterControl
import xenakis.model.SynthControls
import xenakis.model.SynthObject

class ControlAssignmentView(private val obj: SynthObject) : VBox(), SynthControls.View {
    private val editors = mutableMapOf<String, ControlAssignmentEditor>()

    init {
        obj.controls.addView(this)
        prefWidth = 500.0
    }

    override fun addedControl(parameter: String, control: ParameterControl) {
        val spec = obj.getSpec(parameter)
        val editor = ControlAssignmentEditor(obj, parameter, spec)
        editor.setControl(control)
        editors[parameter] = editor
        children.add(editor)
    }

    override fun removedControl(parameter: String, control: ParameterControl) {
        val editor = editors.remove(parameter)
        children.remove(editor)
    }

    override fun reassignedControl(parameter: String, oldControl: ParameterControl, control: ParameterControl) {
        val editor = editors[parameter] ?: error("Editor for parameter '$parameter' not found")
        editor.setControl(control)
    }

    companion object {
        fun create(obj: SynthObject): ControlAssignmentView {
            val view = ControlAssignmentView(obj)
            return view
        }

    }
}