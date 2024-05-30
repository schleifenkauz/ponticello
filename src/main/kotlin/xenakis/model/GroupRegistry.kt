package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.core.editor.ListenerManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.impl.SuperColliderContext

@Serializable
data class GroupRegistry(private val order: MutableList<GroupObject> = mutableListOf(GroupObject.DEFAULT)) {
    @Transient
    private lateinit var context: Context

    @Transient
    private val views = ListenerManager.createWeakListenerManager<View>()

    @Transient
    val groupReferences = ListenerManager.createWeakListenerManager<GroupReference>()

    fun initialize(context: Context) {
        this.context = context
        context[GroupRegistry] = this
        for (group in order) {
            group.initialize(context)
        }
    }

    fun addView(view: View) {
        views.addListener(view)
        for ((index, group) in order.withIndex()) {
            view.addedGroup(group, index)
        }
    }

    fun asList(): List<GroupObject> = order

    fun add(group: GroupObject) {
        val groupBefore = order.last()
        val index = order.size
        order.add(group)
        group.initialize(context)
        setupGroup(group, groupBefore, context[SuperColliderClient])
        views.notifyListeners { addedGroup(group, index) }
    }

    fun hasGroup(name: String) = order.any { it.name.now == name }

    fun remove(group: GroupObject, replacement: GroupObject? = null) {
        check(group != GroupObject.DEFAULT) { "attempt to remove default group" }
        val index = order.indexOf(group)
        if (index == -1) error("$group not registered")
        order.removeAt(index)
        context[SuperColliderClient].run("${group.variableName}.free; ${group.variableName} = nil;")
        views.notifyListeners { removedGroup(group, index) }
        groupReferences.notifyListeners {
            if (this.group == group) {
                this.group = replacement ?: GroupObject.DEFAULT
            }
        }
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
        views.notifyListeners { movedGroup(group, fromIndex, toIndex) }
    }

    fun hasReferences(group: GroupObject) = groupReferences.listeners.any { ref -> ref.group == group }

    private fun setupGroup(group: GroupObject, groupBefore: GroupObject?, context: SuperColliderContext) {
        if (group == GroupObject.DEFAULT) {
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
            if (group != GroupObject.DEFAULT) {
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

    fun indexOf(group: GroupObject): Int = asList().indexOf(group)

    interface View {
        fun addedGroup(group: GroupObject, index: Int)

        fun removedGroup(group: GroupObject, index: Int)

        fun movedGroup(group: GroupObject, fromIndex: Int, toIndex: Int)
    }

    companion object : PublicProperty<GroupRegistry> by publicProperty("GroupRegistry")
}