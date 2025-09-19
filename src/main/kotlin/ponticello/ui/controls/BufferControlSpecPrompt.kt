package ponticello.ui.controls

import fxutils.controls.CheckBox
import fxutils.controls.IntSpinner
import fxutils.prompt.DetailPane
import javafx.scene.Node
import ponticello.model.obj.ParameterizedObject
import ponticello.sc.BufferControlSpec

class BufferControlSpecPrompt(
    parameterName: String, parentObject: ParameterizedObject?, title: String,
    initialSpec: BufferControlSpec,
) : ControlSpecPrompt<BufferControlSpec, DetailPane>(parameterName, parentObject, title) {
    override val content: DetailPane = DetailPane(labelWidth = 120.0)

    private val channelsSpinner = IntSpinner(1, 12, initialSpec.channels).minColumns(2) named "Channels"
    private val inlineDisplayOption = CheckBox(initialSpec.inlineDisplay) named "Inline display"
    private val displaySpectrogramOption = CheckBox(initialSpec.displaySpectrogram) named "Spectrogram"

    override fun makeSpec(): BufferControlSpec = BufferControlSpec(
        channelsSpinner.value(),
        inlineDisplayOption.isSelected, displaySpectrogramOption.isSelected
    )

    override fun onReceiveFocus() {
        channelsSpinner.requestFocus()
    }

    private infix fun <N : Node> N.named(name: String): N = also { content.addItem(name, it) }
}