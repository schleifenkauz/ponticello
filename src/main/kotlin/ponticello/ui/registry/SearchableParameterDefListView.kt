package ponticello.ui.registry

import fxutils.prompt.SimpleSearchableListView
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.ParameterizedObject
import ponticello.sc.Identifier
import ponticello.sc.ParameterType
import ponticello.ui.controls.ControlSpecPrompt
import reaktive.value.now

class SearchableParameterDefListView(
    options: List<ParameterDefObject>, title: String,
    private val parentObject: ParameterizedObject? = null,
    private val fixedParameterType: ParameterType? = null,
) : SimpleSearchableListView<ParameterDefObject>(options, title) {
    override fun extractText(option: ParameterDefObject): String = option.name.now

    override fun displayText(option: ParameterDefObject): String = option.simpleString()

    override fun makeOption(text: String): ParameterDefObject? {
        if (!Identifier.isValid(text)) return null
        Thread.sleep(50)
        val type = fixedParameterType ?: SimpleSearchableListView(ParameterType.regularTypes, "Parameter type")
            .showPopup(anchor, ownerWindow, initialOption = ParameterType.Numerical) ?: return null
        val prompt = ControlSpecPrompt.create(text, parentObject, type) ?: return null
        val spec = prompt.showDialog(ownerWindow, anchor)
        return ParameterDefObject(text, spec)
    }
}