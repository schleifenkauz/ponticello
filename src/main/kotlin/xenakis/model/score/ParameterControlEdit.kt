package xenakis.model.score

import hextant.undo.AbstractEdit
import xenakis.model.score.ParameterControlList.NamedParameterControl
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.ControlSpec

abstract class ParameterControlEdit(protected val control: NamedParameterControl): AbstractEdit() {
    class ReassignControl(
        control: NamedParameterControl,
        private val oldControl: ParameterControl,
        private val newControl: ParameterControl,
    ) : ParameterControlEdit(control) {
        override val actionDescription: String
            get() = "Reassign controls"

        override fun doUndo() {
            control.reassign(oldControl)
        }

        override fun doRedo() {
            control.reassign(newControl)
        }
    }

    class EditCustomSpec(
        control: NamedParameterControl,
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