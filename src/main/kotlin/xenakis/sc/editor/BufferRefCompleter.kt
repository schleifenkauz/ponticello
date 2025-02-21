package xenakis.sc.editor

import hextant.completion.CompletionStrategy
import hextant.completion.ConfiguredCompleter
import hextant.context.Context
import xenakis.model.obj.BufferObject
import xenakis.ui.launcher.XenakisLauncher

class BufferRefCompleter(
    private val ctx: Context
) : ConfiguredCompleter<Any, BufferObject>(CompletionStrategy.underscore) {
    override fun completionPool(context: Any): Collection<BufferObject> =
        ctx[XenakisLauncher.currentProject].buffers.buffers
}