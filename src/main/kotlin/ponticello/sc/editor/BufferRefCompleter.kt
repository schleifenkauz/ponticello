package ponticello.sc.editor

import hextant.completion.CompletionStrategy
import hextant.completion.ConfiguredCompleter
import hextant.context.Context
import ponticello.model.obj.BufferObject
import ponticello.model.project.BUFFERS
import ponticello.model.project.get
import ponticello.ui.launcher.PonticelloLauncher

class BufferRefCompleter(
    private val ctx: Context
) : ConfiguredCompleter<Any, BufferObject>(CompletionStrategy.underscore) {
    override fun completionPool(context: Any): Collection<BufferObject> =
        ctx[PonticelloLauncher.currentProject][BUFFERS].all()
}