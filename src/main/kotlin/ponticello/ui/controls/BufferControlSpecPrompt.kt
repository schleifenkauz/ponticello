package ponticello.ui.controls

import fxutils.prompt.DetailPane
import fxutils.setFixedWidth
import javafx.scene.Node
import javafx.scene.control.CheckBox
import javafx.scene.control.Spinner
import ponticello.model.obj.ParameterizedObject
import ponticello.sc.BufferControlSpec

class BufferControlSpecPrompt(
    parameterName: String, parentObject: ParameterizedObject?, title: String,
    initialSpec: BufferControlSpec,
) : ControlSpecPrompt<BufferControlSpec, DetailPane>(parameterName, parentObject, title) {
    override val content: DetailPane = DetailPane(labelWidth = 100.0)

    private val channelsSpinner = Spinner<Int>(1, 12, initialSpec.channels)
        .setFixedWidth(50.0) named "Channels"
    private val inlineDisplayBox = CheckBox() named "Inline display"

    init {
        inlineDisplayBox.isSelected = initialSpec.inlineDisplay
    }

    override fun makeSpec(): BufferControlSpec = BufferControlSpec(channelsSpinner.value, inlineDisplayBox.isSelected)

    private infix fun <N : Node> N.named(name: String): N = also { content.addItem(name, it) }
}