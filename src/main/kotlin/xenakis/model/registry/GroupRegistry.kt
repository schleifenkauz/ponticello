package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.Serializable
import xenakis.model.obj.GroupObject

@Serializable
class GroupRegistry private constructor(
    private val order: MutableList<GroupObject>
) : SuperColliderObjectRegistry<GroupObject>() {
    override val objects: MutableList<GroupObject>
        get() = order

    override val objectType: String
        get() = "Group"

    override fun initialize(context: Context) {
        for ((prev, nxt) in order.zipWithNext()) {
            nxt.previous = prev
        }
        super.initialize(context)
        context[GroupRegistry] = this
    }

    override fun add(obj: GroupObject, idx: Int) {
        obj.previous = order.getOrNull(idx - 1)
        super.add(obj, idx)
    }

    override fun onRemoved(obj: GroupObject, idx: Int) {
        super.onRemoved(obj, idx)
        if (idx + 1 in order.indices) order[idx + 1].previous = order[idx]
    }

    fun asList(): List<GroupObject> = order

    fun indexOf(group: GroupObject): Int = asList().indexOf(group)

    override fun getDefault(): GroupObject = objects.find { it.isDefault } ?: GroupObject.DEFAULT

    fun moveGroup(group: GroupObject, deltaIndex: Int) {
        val fromIndex = order.indexOf(group)
        if (fromIndex == -1) error("$group not registered")
        val toIndex = fromIndex + deltaIndex
        if (toIndex !in order.indices) error("invalid deltaIndex: $deltaIndex, fromIndex = $fromIndex")
        order.removeAt(fromIndex)
        order.add(toIndex, group)
        group.previous = order.getOrNull(toIndex - 1)
        context[UndoManager].record(MoveEdit(this, group, fromIndex, toIndex))
        views.notifyListeners { if (this is Listener) movedGroup(group, fromIndex, toIndex) }
    }

    private class MoveEdit(
        private val registry: GroupRegistry,
        private val obj: GroupObject,
        private val fromIndex: Int,
        private val toIndex: Int
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Move group"

        override fun doUndo() {
            registry.moveGroup(obj, fromIndex)
        }

        override fun doRedo() {
            registry.moveGroup(obj, toIndex)
        }
    }

    interface Listener : ObjectRegistry.Listener<GroupObject> {
        fun movedGroup(group: GroupObject, fromIndex: Int, toIndex: Int)
    }

    companion object : PublicProperty<GroupRegistry> by publicProperty("GroupRegistry") {
        fun createDefault() = GroupRegistry(mutableListOf(GroupObject.DEFAULT))
    }
}