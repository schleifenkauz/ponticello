package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.model.obj.GroupObject

@Serializable
class GroupRegistry(
    override val objects: MutableList<GroupObject>
) : ObjectRegistry<GroupObject>() {
    override val objectType: String
        get() = "Group"

    override fun initialize(context: Context) {
        super.initialize(context)
        context[GroupRegistry] = this
    }

    override fun getDefault(): GroupObject = find { g -> g.isDefault } ?: error("Default group not found!")

    companion object : PublicProperty<GroupRegistry> by publicProperty("GroupRegistry") {
        fun createDefault() = GroupRegistry(mutableListOf(GroupObject.DEFAULT))
    }
}