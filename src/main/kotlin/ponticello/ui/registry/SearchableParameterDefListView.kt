package ponticello.ui.registry

import fxutils.SubWindow
import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import javafx.geometry.Point2D
import ponticello.model.obj.*
import ponticello.sc.Identifier
import ponticello.sc.ParameterType
import ponticello.ui.controls.ControlSpecPrompt
import reaktive.value.now

class SearchableParameterDefListView(
    options: List<ParameterDefObject>, title: String,
    private val context: Context,
    private val parentObject: ParameterizedObject? = null,
    private val instrumentObject: InstrumentObject? = parentObject?.def,
    private val fixedParameterType: ParameterType? = null,
) : SimpleSearchableListView<ParameterDefObject>(options, title) {
    override val windowType: SubWindow.Type
        get() = SubWindow.Type.Prompt

    override fun extractText(option: ParameterDefObject): String = option.name.now

    override fun displayText(option: ParameterDefObject): String = option.simpleString()

    override fun makeOption(text: String): ParameterDefObject? {
        if (!Identifier.isValid(text)) return null
        val availableParameterTypes = when (instrumentObject) {
            null -> ParameterType.regularTypes
            is ProcessDefObject -> ParameterType.regularTypes
            is SynthDefObject -> ParameterType.regularTypes - ParameterType.Expr
            else -> emptyList()
        }
        val type = fixedParameterType ?: SimpleSearchableListView(availableParameterTypes, "Parameter type")
            .showPopup(this, initialOption = ParameterType.Numerical) ?: return null
        val anchor = localToScreen(Point2D.ZERO)
        val spec = ControlSpecPrompt.createParameter(text, parentObject, type, window, anchor) ?: return null
        return ParameterDefObject(text, spec).withName(text)
    }
}