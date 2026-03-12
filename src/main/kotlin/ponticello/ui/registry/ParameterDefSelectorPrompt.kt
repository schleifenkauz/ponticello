package ponticello.ui.registry

import fxutils.SubWindow
import fxutils.prompt.SimpleSelectorPrompt
import javafx.geometry.Point2D
import ponticello.model.instr.*
import ponticello.model.obj.withName
import ponticello.sc.Identifier
import ponticello.sc.ParameterType
import ponticello.ui.controls.ControlSpecPrompt
import reaktive.value.now

class ParameterDefSelectorPrompt(
    options: List<ParameterDefObject>, title: String,
    private val parentObject: ParameterizedObject? = null,
    private val instrumentObject: InstrumentObject? = parentObject?.def,
    private val fixedParameterType: ParameterType? = null,
) : SimpleSelectorPrompt<ParameterDefObject>(options, title) {
    override val canCreateItem: Boolean get() = true

    override val windowType: SubWindow.Type
        get() = SubWindow.Type.Prompt

    override fun extractText(option: ParameterDefObject): String = option.name.now

    override fun displayText(option: ParameterDefObject): String = option.simpleString()

    override fun makeOption(text: String): ParameterDefObject? {
        if (!Identifier.isValid(text)) return null
        val availableParameterTypes = when (instrumentObject) {
            null -> ParameterType.regularTypes
            is RoutineDefObject -> ParameterType.regularTypes
            is SynthDefObject -> ParameterType.regularTypes - ParameterType.Expr
            else -> emptyList()
        }
        val type = fixedParameterType ?: SimpleSelectorPrompt(availableParameterTypes, "Parameter type")
            .showPopup(content, initialOption = ParameterType.Numerical) ?: return null
        val anchor = content.localToScreen(Point2D.ZERO)
        val spec = ControlSpecPrompt.createParameter(text, parentObject, type, window, anchor) ?: return null
        return ParameterDefObject(text, spec).withName(text)
    }
}