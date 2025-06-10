package ponticello.ui.controls

import fxutils.controls.IntSpinner
import fxutils.prompt.DetailPane
import javafx.scene.Node
import javafx.scene.control.CheckBox
import ponticello.model.obj.ParameterizedObject
import ponticello.sc.BufferControlSpec

class BufferControlSpecPrompt(
    parameterName: String, parentObject: ParameterizedObject?, title: String,
    initialSpec: BufferControlSpec,
) : ControlSpecPrompt<BufferControlSpec, DetailPane>(parameterName, parentObject, title) {
    override val content: DetailPane = DetailPane(labelWidth = 120.0)

    private val channelsSpinner = IntSpinner(1, 12, initialSpec.channels).minColumns(2) named "Channels"
    private val inlineDisplayBox = CheckBox() named "Inline display"

    init {
        inlineDisplayBox.isSelected = initialSpec.inlineDisplay
    }

    override fun makeSpec(): BufferControlSpec = BufferControlSpec(channelsSpinner.value(), inlineDisplayBox.isSelected)

    private infix fun <N : Node> N.named(name: String): N = also { content.addItem(name, it) }
}