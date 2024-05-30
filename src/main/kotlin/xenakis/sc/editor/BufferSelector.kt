package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.SimpleChoiceEditor
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.sc.Buffer
import xenakis.sc.NoBuffer
import xenakis.ui.XenakisController

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class BufferSelector(context: Context, value: Buffer = NoBuffer) : SimpleChoiceEditor<Buffer>(context, value) {
    override fun choices(): List<Buffer> = context[XenakisController.currentProject].buffers.buffers + NoBuffer

    override fun toString(choice: Buffer): String = choice.name.now
}