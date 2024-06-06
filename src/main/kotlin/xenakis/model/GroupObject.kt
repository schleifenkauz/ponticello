package xenakis.model

import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ScWriter
import xenakis.model.SuperColliderObject.LiveCycleType

@Serializable
class GroupObject(
    override val mutableName: ReactiveVariable<String>,
    val isDefault: Boolean = false
) : AbstractSuperColliderObject() {
    override val variableName: String get() = if (isDefault) "s.defaultGroup" else "~grp_${name.now}"

    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.ServerTree

    var previous: GroupObject? = null
        set(prev) {
            if (field == prev) return
            field = prev
            redefine()
        }

    override fun ScWriter.allocateServerObject() {
        if (!isDefault) +"$variableName = Group.new"
        val prev = previous
        if (prev != null) {
            client.run("${variableName}.moveAfter(${prev.variableName});")
        } else {
            client.run("$variableName.moveToHead;")
        }
    }

    override fun canRenameTo(newName: String): Boolean = !context[GroupRegistry].has(newName)

    override fun createReference(): GroupObjectReference = GroupObjectReference(this)

    companion object {
        val DEFAULT = GroupObject(reactiveVariable("default"), isDefault = true)
    }
}