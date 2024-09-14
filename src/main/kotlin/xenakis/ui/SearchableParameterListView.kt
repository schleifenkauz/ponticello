package xenakis.ui

import hextant.context.Context
import reaktive.value.now
import xenakis.model.ParameterDefObject
import xenakis.model.Settings
import xenakis.model.SynthDefObject
import xenakis.sc.BufferControlSpec
import xenakis.sc.BusControlSpec
import xenakis.sc.GroupControlSpec
import xenakis.sc.NumericalControlSpec

class SearchableParameterListView(private val context: Context, private val synthDef: SynthDefObject) :
    SimpleSearchableListView<ParameterDefObject>(synthDef.parameters.now) {
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
        //todo more options
        val defaultSpec = context[Settings].getDefaultControlSpec(text) ?: return null
        return ParameterDefObject(text, defaultSpec)
    }
}