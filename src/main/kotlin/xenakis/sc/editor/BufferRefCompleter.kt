package xenakis.sc.editor

import hextant.completion.CompletionStrategy
import hextant.completion.ConfiguredCompleter
import hextant.context.Context
import xenakis.sc.Buffer
import xenakis.ui.XenakisController

class BufferRefCompleter(
    private val ctx: Context
) : ConfiguredCompleter<Any, Buffer>(CompletionStrategy.underscore) {
    override fun completionPool(context: Any): Collection<Buffer> =
        ctx[XenakisController.currentProject].buffers.buffers
}