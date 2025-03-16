package xenakis.ui.controls

import javafx.scene.layout.VBox
import reaktive.value.now
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.ParameterControl
import xenakis.model.score.ParameterControls
import xenakis.sc.ControlSpec
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.registry.ObjectBoxList
import xenakis.ui.registry.ObjectBoxSource

class ControlAssignmentView(
    private val obj: ParameterizedObject
) : VBox(), ParameterControls.Listener, ObjectBoxSource<NamedParameterControl> {
    private val editors = ObjectBoxList(this)

    override val items: MutableList<NamedParameterControl> =
        obj.controls.controlMap.mapTo(mutableListOf()) { (param, control) ->
            val editor = ControlAssignmentEditor(obj, param)
            editor.setControl(control)
            NamedParameterControl(obj.controls, param, editor)
        }

    init {
        obj.controls.addListener(this)
    }

    override fun addedControl(parameter: String, control: ParameterControl) {
        if (obj.getSpec(parameter) == null) return
        val editor = ControlAssignmentEditor(obj, parameter)
        editor.setControl(control)
        val named = NamedParameterControl(obj.controls, parameter, editor)
        editors.add(obj.controls.controlMap.size - 1, named) //TODO which index?
        if (scene != null && scene.window != obj.context[primaryStage]) scene.window.sizeToScene()
    }

    override fun removedControl(parameter: String, control: ParameterControl) {
        val editor = getEditor(parameter)
        editors.remove(editor)
        if (scene != null && scene.window != obj.context[primaryStage]) scene.window.sizeToScene()
    }

    private fun getEditor(parameter: String) =
        items.find { e -> e.name.now == parameter } ?: error("Parameter $parameter not displayed")

    override fun reassignedControl(parameter: String, oldControl: ParameterControl, control: ParameterControl) {
        val named = getEditor(parameter)
        named.editor.setControl(control)
    }

    override fun changedSpec(parameter: String, newSpec: ControlSpec) {
        if (parameter !in obj.controls.controlMap) return
        val named = getEditor(parameter)
        named.editor.setControl(obj.controls[parameter])
    }
}