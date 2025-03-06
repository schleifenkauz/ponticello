package xenakis.ui.registry

import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import reaktive.value.now
import xenakis.model.Settings
import xenakis.model.obj.ParameterDefObject
import xenakis.model.obj.ParameterizedObject
import xenakis.sc.*

class SearchableParameterListView(
    private val context: Context,
    title: String,
    val obj: ParameterizedObject,
    val parameters: List<ParameterDefObject>
) : SimpleSearchableListView<ParameterDefObject>(parameters, title) {
    override fun extractText(option: ParameterDefObject): String = option.name.now

    override fun displayText(option: ParameterDefObject): String {
        val type = when (option.spec.now) {
            is NumericalControlSpec -> "num"
            is BufferControlSpec -> "buf"
            is BusControlSpec -> "bus"
            is GroupControlSpec -> "group"
        }
        return "${option.name.now} ($type)"
    }

    override fun makeOption(text: String): ParameterDefObject? {
        if (!Identifier.isValid(text)) return null
        if (text in obj.controls.controlMap) return null
        val defaultSpec = context[Settings].getDefaultControlSpec(text) ?: NumericalControlSpec.DEFAULT
        //return ControlSpecPrompt(obj, text, defaultSpec).showDialog(context) //TODO why is the window unresponsive?
        return ParameterDefObject(text, defaultSpec)
    }
}