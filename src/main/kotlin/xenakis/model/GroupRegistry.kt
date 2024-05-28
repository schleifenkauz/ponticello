package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.core.editor.ViewManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.SuperColliderContext
import xenakis.impl.UDPSuperColliderClient
import xenakis.sc.Group

@Serializable
data class GroupRegistry(private val order: MutableList<Group> = mutableListOf(Group.DEFAULT)) {
    @Transient
    private lateinit var client: UDPSuperColliderClient

    @Transient
    private val views = ViewManager.createWeakViewManager<View>()

    @Transient
    val groupReferences = ViewManager.createWeakViewManager<GroupReference>()

    fun initialize(context: Context) {
        client = context[UDPSuperColliderClient]
        context[GroupRegistry] = this
    }

    fun addView(view: View) {
        views.addView(view)
        for ((index, group) in order.withIndex()) {
            view.addedGroup(group, index)
        }
    }

    fun asList(): List<Group> = order

    fun add(group: Group) {
        val groupBefore = order.last()
        val index = order.size
        order.add(group)
        setupGroup(group, groupBefore, client)
        views.notifyViews { addedGroup(group, index) }
    }

    fun remove(group: Group, replacement: Group? = null) {
        check(group != Group.DEFAULT) { "attempt to remove default group" }
        val index = order.indexOf(group)
        if (index == -1) error("$group not registered")
        order.removeAt(index)
        client.postAsync("${group.variableName}.free; ${group.variableName} = nil;")
        views.notifyViews { removedGroup(group, index) }
        groupReferences.notifyViews {
            if (this.group == group) {
                this.group = replacement ?: Group.DEFAULT
            }
        }
    }

    fun renameGroup(group: Group, name: String) {
        client.postAsync("$name = ${group.variableName}; ${group.variableName} = nil;")
        val index = order.indexOf(group)
        if (index == -1) error("$group not registered")
        group.name = name
        views.notifyViews { renamedGroup(group, index) }
    }

    fun moveGroup(group: Group, deltaIndex: Int) {
        val fromIndex = order.indexOf(group)
        if (fromIndex == -1) error("$group not registered")
        val toIndex = fromIndex + deltaIndex
        if (toIndex !in order.indices) error("invalid deltaIndex: $deltaIndex, fromIndex = $fromIndex")
        order.removeAt(fromIndex)
        order.add(toIndex, group)
        if (toIndex == 0) {
            client.postAsync("${group.variableName}.moveBefore(${order[0].variableName});")
        } else {
            val groupBefore = order[toIndex - 1]
            client.postAsync("${group.variableName}.moveAfter(${groupBefore.variableName});")
        }
        views.notifyViews { movedGroup(group, fromIndex, toIndex) }
    }

    fun hasReferences(group: Group) = groupReferences.views.any { ref -> ref.group == group }

    private fun setupGroup(group: Group, groupBefore: Group?, context: SuperColliderContext) {
        if (group == Group.DEFAULT) {
            if (groupBefore != null) {
                context.postAsync("s.defaultGroup.moveAfter(${groupBefore.variableName});")
            }
        } else {
            if (groupBefore != null) {
                context.postAsync("${group.variableName} = Group.after(${groupBefore.variableName});")
            } else {
                context.postAsync("${group.variableName} = Group.new;")
            }
        }
    }

    fun setupGroups(context: SuperColliderContext) = context.postAsync {
        for (group in order) {
            if (group != Group.DEFAULT) {
                appendBlock("if (${group.variableName} != nil)") {
                    +"${group.variableName}.free"
                    +"${group.variableName} = nil"
                }
                appendLine(";")
            }
        }
        setupGroup(order[0], null, this.context)
        for ((before, after) in order.zipWithNext()) {
            setupGroup(after, before, this.context)
        }
    }

    fun indexOf(group: Group): Int = asList().indexOf(group)

    interface View {
        fun addedGroup(group: Group, index: Int)

        fun removedGroup(group: Group, index: Int)

        fun renamedGroup(group: Group, index: Int)

        fun movedGroup(group: Group, fromIndex: Int, toIndex: Int)
    }

    companion object : PublicProperty<GroupRegistry> by publicProperty("GroupRegistry")
}