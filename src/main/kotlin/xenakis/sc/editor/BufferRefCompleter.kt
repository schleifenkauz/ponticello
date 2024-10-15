package xenakis.sc.editor

import hextant.completion.CompletionStrategy
import hextant.completion.ConfiguredCompleter
import hextant.context.Context
import xenakis.model.obj.BufferObject
import xenakis.ui.XenakisController

class BufferRefCompleter(
    private val ctx: Context
) : ConfiguredCompleter<Any, BufferObject>(CompletionStrategy.underscore) {
    override fun completionPool(context: Any): Collection<BufferObject> =
        ctx[XenakisController.currentProject].buffers.buffers
}