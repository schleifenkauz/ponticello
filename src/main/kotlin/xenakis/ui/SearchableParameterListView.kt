package xenakis.ui

import hextant.context.Context
import reaktive.value.now
import xenakis.model.ParameterDefObject
import xenakis.model.Settings
import xenakis.model.SynthObject
import xenakis.sc.*

class SearchableParameterListView(
    private val context: Context, val parameters: List<ParameterDefObject>, val obj: SynthObject, title: String
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