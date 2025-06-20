package ponticello.ui.registry

import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import ponticello.model.GlobalSettings
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.ParameterizedObject
import ponticello.sc.Identifier
import ponticello.sc.NumericalControlSpec
import reaktive.value.now

class SearchableParameterListView(
    private val context: Context,
    title: String,
    val obj: ParameterizedObject,
    val parameters: List<ParameterDefObject>
) : SimpleSearchableListView<ParameterDefObject>(parameters, title) {
    override fun extractText(option: ParameterDefObject): String = option.name.now

    override fun displayText(option: ParameterDefObject): String = option.simpleString()

    override fun makeOption(text: String): ParameterDefObject? {
        if (!Identifier.isValid(text)) return null
        if (text in obj.controls.controlMap) return null
        val defaultSpec = context[GlobalSettings].getDefaultControlSpec(text) ?: NumericalControlSpec.DEFAULT
        //return ControlSpecPrompt(obj, text, defaultSpec).showDialog(context) //TODO why is the window unresponsive?
        return ParameterDefObject(text, defaultSpec)
    }
}