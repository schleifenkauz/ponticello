package xenakis.sc.editor

import hextant.completion.Completer
import hextant.completion.Completion

object ReferenceCompleter : Completer<Any, String> {
    override fun completions(context: Any, input: String): Collection<Completion<String>> {
        return emptyList()
    }
}