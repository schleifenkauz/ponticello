package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.GroupObject
import xenakis.model.GroupRegistry
import xenakis.model.ObjectReference
import xenakis.model.ObjectRegistry
import kotlin.reflect.KClass

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class GroupSelector(
    context: Context,
    selected: ReactiveVariable<ObjectReference> = reactiveVariable(ObjectReference("<invalid>")),
) : ObjectSelector<GroupObject, ObjectReference>(context, selected) {
    override fun getRegistry(context: Context): ObjectRegistry<GroupObject> = context[GroupRegistry]

    override val objectClass: KClass<GroupObject>
        get() = GroupObject::class

    override fun createNewObject(name: String): GroupObject {
        val obj = GroupObject(reactiveVariable(name))
        return obj
    }
}