package xenakis.model.obj

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.obj.SuperColliderObject.LiveCycleType
import xenakis.model.registry.GroupRegistry
import xenakis.sc.client.ScWriter

@Serializable
class GroupObject(
    override val mutableName: ReactiveVariable<String>,
    val isDefault: Boolean = false
) : AbstractSuperColliderObject() {
    override val superColliderName: String get() = if (isDefault) "s.defaultGroup" else "~grp_${name.now}"

    override val functionName: String
        get() = if (isDefault) "~default_group_init" else super.functionName

    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.ServerTree

    override val canDelete: Boolean
        get() = !isDefault

    @Transient
    var previous: GroupObject? = null
        set(prev) {
            if (field == prev) return
            field = prev
            if (initialized) client.run { moveAfter(prev) }
        }

    override fun ScWriter.allocateServerObject() {
        if (!isDefault) +"$superColliderName = Group.new"
        val prev = previous
        moveAfter(prev)
    }

    private fun ScWriter.moveAfter(prev: GroupObject?) {
        if (prev != null) {
            +"${superColliderName}.moveAfter(${prev.superColliderName})"
        } else if (!isDefault) {
            +"$superColliderName.moveToHead"
        }
    }

    override fun canRenameTo(newName: String): Boolean = !context[GroupRegistry].has(newName)

    companion object {
        val DEFAULT = GroupObject(reactiveVariable("default"), isDefault = true)
    }
}