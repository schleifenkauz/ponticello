package ponticello.ui.controls

import fxutils.centerChildren
import fxutils.prompt.SimpleSearchableListView
import fxutils.undo.UndoManager
import javafx.scene.control.Spinner
import javafx.scene.layout.HBox
import ponticello.model.obj.ParameterizedObject
import ponticello.sc.BusControlSpec
import ponticello.sc.Rate

class BusControlSpecPrompt(
    parameterName: String, parentObject: ParameterizedObject?, title: String,
    initialSpec: BusControlSpec
) : ControlSpecPrompt<BusControlSpec, HBox>(parameterName, parentObject, title) {
    private var rate = initialSpec.rate

    private val channelsSpinner = Spinner<Int>(1, 12, initialSpec.channels)

    private val rateSelector = SimpleSearchableListView(Rate.entries, "Choose rate")
        .selectorButton(this::rate, undoManager = parentObject?.context?.get(UndoManager))

    override val content: HBox = HBox(5.0, rateSelector, channelsSpinner).centerChildren()

    override fun makeSpec(): BusControlSpec = BusControlSpec(rate, channelsSpinner.value)
}