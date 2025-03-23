package xenakis.ui.controls

import javafx.scene.control.Spinner
import xenakis.model.obj.ParameterizedObject
import xenakis.sc.BufferControlSpec

class BufferControlSpecPrompt(
    parameterName: String, parentObject: ParameterizedObject?, title: String,
    initialSpec: BufferControlSpec,
) : ControlSpecPrompt<BufferControlSpec, Spinner<Int>>(parameterName, parentObject, title) {
    override val content: Spinner<Int> = Spinner<Int>(1, 12, initialSpec.channels)

    override fun makeSpec(): BufferControlSpec = BufferControlSpec(content.value)
}