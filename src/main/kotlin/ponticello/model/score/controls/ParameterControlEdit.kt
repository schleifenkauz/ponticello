package ponticello.model.score.controls

import fxutils.undo.AbstractEdit
import ponticello.sc.ControlSpec

abstract class ParameterControlEdit(protected val control: ParameterControlList.NamedParameterControl) :
    AbstractEdit() {
    class ReassignControl(
        control: ParameterControlList.NamedParameterControl,
        private val oldControl: ParameterControl,
        private val newControl: ParameterControl,
    ) : ParameterControlEdit(control) {
        override val actionDescription: String
            get() = "Reassign controls"

        override fun doUndo() {
            control.reassign(oldControl.copy())
        }

        override fun doRedo() {
            control.reassign(newControl.copy())
        }
    }

    class EditCustomSpec(
        control: ParameterControlList.NamedParameterControl,
        private val extraSpecBefore: ControlSpec?,
        private val extraSpecAfter: ControlSpec?,
    ) : ParameterControlEdit(control) {
        override val actionDescription: String
            get() = when {
                extraSpecBefore != null && extraSpecAfter == null -> "Reset parameter spec"
                extraSpecBefore == null && extraSpecAfter != null -> "Add custom parameter spec"
                else -> "Modify extra spec"
            }

        override fun doUndo() {
            control.setCustomSpec(extraSpecBefore)
        }

        override fun doRedo() {
            control.setCustomSpec(extraSpecAfter)
        }
    }

}