package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.Snapshot
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import xenakis.model.BufferObject
import xenakis.model.BufferObjectReference
import xenakis.model.BufferRegistry
import xenakis.model.ObjectRegistry

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class BufferSelector(
    context: Context, initialValue: BufferObjectReference = BufferObject.defaultBuffer.createReference()
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