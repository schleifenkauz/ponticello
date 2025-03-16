package xenakis.ui.controls

import reaktive.value.*
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.AbstractRenamableObject
import xenakis.model.obj.RenamableObject
import xenakis.model.score.ParameterControls

class NamedParameterControl(
    private val controls: ParameterControls,
    private val parameter: String,
    val editor: ControlAssignmentEditor,
) : RenamableObject, AbstractContextualObject() {
    override val isAdded: ReactiveBoolean
        get() = reactiveValue(false)
    override val name: ReactiveValue<String>
        get() = reactiveValue(parameter)

    override fun canRenameTo(newName: String): Boolean = newName !in controls.controlMap

    override fun rename(newName: String) {
        controls.renameControl(name.now, newName)
    }
}