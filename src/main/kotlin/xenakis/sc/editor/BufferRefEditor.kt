package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.SimpleChoiceEditor
import xenakis.sc.Buffer
import xenakis.sc.NoBuffer
import xenakis.ui.XenakisController

class BufferRefEditor(context: Context, value: Buffer = NoBuffer) : SimpleChoiceEditor<Buffer>(context, value) {
    override fun choices(): List<Buffer> = context[XenakisController.currentProject].buffers.buffers

    override fun toString(choice: Buffer): String = choice.name
}