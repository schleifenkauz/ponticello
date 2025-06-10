package ponticello.ui.controls

import fxutils.controls.CheckBox
import fxutils.controls.IntSpinner
import fxutils.prompt.DetailPane
import fxutils.prompt.SimpleSearchableListView
import fxutils.undo.UndoManager
import javafx.scene.Node
import ponticello.model.obj.ParameterizedObject
import ponticello.sc.BusControlSpec
import ponticello.sc.Rate

class BusControlSpecPrompt(
    parameterName: String, parentObject: ParameterizedObject?, title: String,
    initialSpec: BusControlSpec,
) : ControlSpecPrompt<BusControlSpec, DetailPane>(parameterName, parentObject, title) {
    override val content = DetailPane(labelWidth = 120.0)

    private var rate = initialSpec.rate

    private val channelsSpinner = IntSpinner(1, 12, initialSpec.channels).minColumns(2) named "Channels"

    private val rateSelector = SimpleSearchableListView(Rate.entries, "Choose rate")
        .selectorButton(this::rate, undoManager = parentObject?.context?.get(UndoManager)) named "Rate"

    private val inlineDisplayBox = CheckBox(initialSpec.inlineDisplay) named "Inline display"

    override fun makeSpec(): BusControlSpec = BusControlSpec(rate, channelsSpinner.value(), inlineDisplayBox.isSelected)

    private infix fun <N : Node> N.named(name: String): N = also { content.addItem(name, it) }
}