package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.BufferObject
import xenakis.model.BufferRegistry
import xenakis.model.ObjectReference
import xenakis.model.ObjectRegistry
import kotlin.reflect.KClass

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class BufferSelector(
    context: Context,
    selected: ReactiveVariable<ObjectReference?> = reactiveVariable(null)
) : ObjectSelector<BufferObject, ObjectReference?>(context, selected) {
    override fun getRegistry(context: Context): ObjectRegistry<*> = context[BufferRegistry]

    override val objectClass: KClass<BufferObject>
        get() = BufferObject::class

    override fun createNewObject(name: String): BufferObject? {
        throw UnsupportedOperationException("BufferSelector doesn't support creating new object")
    }
}