package xenakis.sc.editor

import hextant.completion.CompletionStrategy
import hextant.completion.ConfiguredCompleter
import hextant.context.Context
import xenakis.sc.ParameterDef

class ParameterDefCompleter(val context: Context) : ConfiguredCompleter<Any, ParameterDef>(CompletionStrategy.simple) {
    override fun completionPool(context: Any): Collection<ParameterDef> =
        listOf(ParameterDef.freq, ParameterDef.amp, ParameterDef.amp)
}