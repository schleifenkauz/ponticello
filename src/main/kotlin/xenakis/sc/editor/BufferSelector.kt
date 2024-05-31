package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.Snapshot
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import xenakis.model.*

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class BufferSelector(
    context: Context, initialValue: BufferObjectReference = NoBuffer.createReference()
) : ObjectSelector<BufferObject, BufferObjectReference>(context, initialValue) {
    override val registry: ObjectRegistry<BufferObject>
        get() = context[BufferRegistry]

    override fun createNewObject(name: String): BufferObject {
        TODO("Not yet implemented")
    }

    override fun createSnapshot(): Snapshot<*> {
        TODO("Not yet implemented")
    }
}