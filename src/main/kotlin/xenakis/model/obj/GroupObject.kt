package xenakis.model.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectRegistry

@Serializable
class GroupObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val isDefault: Boolean = false
) : AbstractRenamableObject() {
    val superColliderName: String get() = if (isDefault) "s.defaultGroup" else "~grp_${name.now}"

    override val registry: ObjectRegistry<*>
        get() = context[GroupRegistry]

    override fun canRenameTo(newName: String): Boolean = !context[GroupRegistry].has(newName)

    companion object {
        val DEFAULT = GroupObject(reactiveVariable("default"), isDefault = true)
    }
}