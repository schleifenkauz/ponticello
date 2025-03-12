package xenakis.ui.registry

import fxutils.prompt.SimpleSearchableListView
import reaktive.value.now
import xenakis.model.obj.ParameterDefObject
import xenakis.sc.Identifier
import xenakis.sc.NumericalControlSpec

class SearchableParameterDefListView(
    options: List<ParameterDefObject>, title: String
) : SimpleSearchableListView<ParameterDefObject>(options, title) {
    override fun extractText(option: ParameterDefObject): String = option.name.now

    override fun displayText(option: ParameterDefObject): String = option.simpleString()

    override fun makeOption(text: String): ParameterDefObject? {
        if (!Identifier.isValid(text)) return null
        return ParameterDefObject(text, NumericalControlSpec.DEFAULT)
    }
}