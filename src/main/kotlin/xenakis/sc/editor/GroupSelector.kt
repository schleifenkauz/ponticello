package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.obj.GroupObject
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import kotlin.reflect.KClass

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class GroupSelector(
    context: Context,
    selected: ReactiveVariable<ObjectReference?>,
) : ObjectSelector<GroupObject, ObjectReference?>(context, selected) {
    constructor(context: Context) : this(
        context, reactiveVariable(context[GroupRegistry].getDefault().reference())
    )

    override fun getRegistry(context: Context): ObjectRegistry<*> = context[GroupRegistry]

    override val objectClass: KClass<GroupObject>
        get() = GroupObject::class

    override fun createNewObject(name: String): GroupObject = GroupObject(reactiveVariable(name))
}