package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.undo.AbstractEdit
import kotlinx.serialization.Serializable
import xenakis.impl.SuperColliderClient
import xenakis.impl.SuperColliderContext

@Serializable
data class GroupRegistry(
    private val order: MutableList<GroupObject> = mutableListOf(GroupObject.DEFAULT)
) : ObjectRegistry<GroupObject>() {
    override val objects: MutableList<GroupObject>
        get() = order

    override val objectType: String
        get() = "Group"

    override fun initialize(context: Context) {
        context[GroupRegistry] = this
        super.initialize(context)
    }

    fun asList(): List<GroupObject> = order

    fun indexOf(group: GroupObject): Int = asList().indexOf(group)

    override fun getDefault(): GroupObject = objects.find { it.isDefault } ?: GroupObject.DEFAULT

    override fun onAdded(obj: GroupObject, idx: Int) {
        val groupBefore = order.getOrNull(idx - 1)
        setupGroup(obj, groupBefore, context[SuperColliderClient])
    }

    override fun onRemoved(obj: GroupObject, idx: Int) {
        context[SuperColliderClient].run("${obj.variableName}.free; ${obj.variableName} = nil;")
    }

    fun moveGroup(group: GroupObject, deltaIndex: Int) {
        val fromIndex = order.indexOf(group)
        if (fromIndex == -1) error("$group not registered")
        val toIndex = fromIndex + deltaIndex
        if (toIndex !in order.indices) error("invalid deltaIndex: $deltaIndex, fromIndex = $fromIndex")
        order.removeAt(fromIndex)
        order.add(toIndex, group)
        if (toIndex == 0) {
            context[SuperColliderClient].run("${group.variableName}.moveBefore(${order[0].variableName});")
        } else {
            val groupBefore = order[toIndex - 1]
            context[SuperColliderClient].run("${group.variableName}.moveAfter(${groupBefore.variableName});")
        }
        views.notifyListeners { if (this is View) movedGroup(group, fromIndex, toIndex) }
    }

    private fun setupGroup(group: GroupObject, groupBefore: GroupObject?, context: SuperColliderContext) {
        if (group.isDefault) {
            if (groupBefore != null) {
                context.run("s.defaultGroup.moveAfter(${groupBefore.variableName});")
            }
        } else {
            if (groupBefore != null) {
                context.run("${group.variableName} = Group.after(${groupBefore.variableName});")
            } else {
                context.run("${group.variableName} = Group.new;")
            }
        }
    }

    fun SuperColliderContext.setupGroups() = run {
        for (group in order) {
            if (!group.isDefault) {
                appendBlock("if (${group.variableName} != nil)") {
                    +"${group.variableName}.free"
                    +"${group.variableName} = nil"
                }
                appendLine(";")
            }
        }
        setupGroup(order[0], null, context)
        for ((before, after) in order.zipWithNext()) {
            setupGroup(after, before, context)
        }
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

    interface View : ObjectRegistry.View<GroupObject> {
        fun movedGroup(group: GroupObject, fromIndex: Int, toIndex: Int)
    }

    companion object : PublicProperty<GroupRegistry> by publicProperty("GroupRegistry")
}