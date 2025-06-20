package ponticello.ui.registry

import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import ponticello.model.obj.*
import ponticello.sc.Identifier
import ponticello.sc.ParameterType
import ponticello.ui.controls.ControlSpecPrompt
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import reaktive.value.now

class SearchableParameterDefListView(
    options: List<ParameterDefObject>, title: String,
    private val context: Context,
    private val parentObject: ParameterizedObject? = null,
    private val instrumentObject: InstrumentObject? = parentObject?.def,
    private val fixedParameterType: ParameterType? = null,
) : SimpleSearchableListView<ParameterDefObject>(options, title) {
    override fun extractText(option: ParameterDefObject): String = option.name.now

    override fun displayText(option: ParameterDefObject): String = option.simpleString()

    override fun makeOption(text: String): ParameterDefObject? {
        if (!Identifier.isValid(text)) return null
        val availableParameterTypes = when (instrumentObject) {
            null -> ParameterType.regularTypes
            is ProcessDefObject -> ParameterType.regularTypes
            is SynthDefObject -> ParameterType.regularTypes - ParameterType.Object
            else -> emptyList()
        }
        Thread.sleep(100)
        val ownerWindow = context[primaryStage]
        val type = fixedParameterType ?: SimpleSearchableListView(availableParameterTypes, "Parameter type")
            .showPopup(anchor, ownerWindow, initialOption = ParameterType.Numerical) ?: return null
        val spec = ControlSpecPrompt.createParameter(text, parentObject, type, ownerWindow, anchor) ?: return null
        return ParameterDefObject(text, spec).withName(text)
    }
}