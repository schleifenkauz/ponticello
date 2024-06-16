package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.Snapshot
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.*

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class GroupSelector(
    context: Context,
    selected: ReactiveVariable<GroupObjectReference>
) : ObjectSelector<GroupObject, GroupObjectReference>(context, selected), ScExprEditor<GroupObjectReference> {
    constructor(
        context: Context, group: GroupObjectReference = context[GroupRegistry].getDefault().createReference()
    ) : this(context, reactiveVariable(group))

    override val isNullable: Boolean
        get() = false

    override val registry: ObjectRegistry<GroupObject>
        get() = context[GroupRegistry]

    override fun createNewObject(name: String): GroupObject {
        val obj = GroupObject(reactiveVariable(name))
        return obj
    }

    override fun createSnapshot(): Snapshot<*> = Snap()

    private class Snap : ObjectSelector.Snap<GroupObject, GroupObjectReference>() {
        override val serializer: ObjectReference.Serializer<GroupObjectReference>
            get() = GroupObjectReference.Serializer
    }
}