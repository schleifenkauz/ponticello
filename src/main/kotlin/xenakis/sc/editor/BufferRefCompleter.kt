package xenakis.sc.editor

import hextant.completion.CompletionStrategy
import hextant.completion.ConfiguredCompleter
import hextant.context.Context
import xenakis.model.obj.BufferObject
import xenakis.model.project.BUFFERS
import xenakis.model.project.get
import xenakis.ui.launcher.XenakisLauncher

class BufferRefCompleter(
    private val ctx: Context
) : ConfiguredCompleter<Any, BufferObject>(CompletionStrategy.underscore) {
    override fun completionPool(context: Any): Collection<BufferObject> =
        ctx[XenakisLauncher.currentProject][BUFFERS].all()
}