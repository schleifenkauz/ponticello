package xenakis.sc.editor

import hextant.completion.CompletionStrategy
import hextant.completion.ConfiguredCompleter
import hextant.context.Context
import reaktive.value.now
import xenakis.model.Settings
import xenakis.sc.ParameterDef

class ParameterDefCompleter(val context: Context) : ConfiguredCompleter<Any, ParameterDef>(CompletionStrategy.simple) {
    override fun completionPool(context: Any): Collection<ParameterDef> =
        this.context[Settings].defaultParametersDefs.result.now
}