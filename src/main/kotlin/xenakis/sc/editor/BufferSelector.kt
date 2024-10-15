package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.obj.BufferObject
import xenakis.model.registry.BufferRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
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