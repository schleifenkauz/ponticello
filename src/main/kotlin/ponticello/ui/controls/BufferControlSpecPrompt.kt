package ponticello.ui.controls

import javafx.scene.control.Spinner
import ponticello.model.obj.ParameterizedObject
import ponticello.sc.BufferControlSpec

class BufferControlSpecPrompt(
    parameterName: String, parentObject: ParameterizedObject?, title: String,
    initialSpec: BufferControlSpec,
) : ControlSpecPrompt<BufferControlSpec, Spinner<Int>>(parameterName, parentObject, title) {
    override val content: Spinner<Int> = Spinner<Int>(1, 12, initialSpec.channels)

    override fun makeSpec(): BufferControlSpec = BufferControlSpec(content.value)
}