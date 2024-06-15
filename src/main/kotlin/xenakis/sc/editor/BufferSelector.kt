package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.Snapshot
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import xenakis.model.*

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class BufferSelector(
    context: Context, initialValue: BufferObjectReference = BufferObject.defaultBuffer.createReference()
) : ObjectSelector<BufferObject, BufferObjectReference>(context, initialValue), ScExprEditor<BufferObjectReference> {
    override val isNullable: Boolean
        get() = false
    override val registry: ObjectRegistry<BufferObject>
        get() = context[BufferRegistry]

    override fun createNewObject(name: String): BufferObject? {
        throw UnsupportedOperationException("BufferSelector doesn't support creating new object")
    }

    override fun createSnapshot(): Snapshot<*> = Snap()

    private class Snap : ObjectSelector.Snap<BufferObject, BufferObjectReference>() {
        override val serializer: ObjectReference.Serializer<BufferObjectReference>
            get() = BufferObjectReference.Serializer
    }
}