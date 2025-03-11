package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.model.obj.GroupObject

@Serializable
class GroupRegistry private constructor(
    private val order: MutableList<GroupObject>
) : ObjectRegistry<GroupObject>() {
    override val objects: MutableList<GroupObject>
        get() = order

    override val objectType: String
        get() = "Group"

    override fun initialize(context: Context) {
        super.initialize(context)
        context[GroupRegistry] = this
    }

    fun getDefaultGroup() = objects.find { g -> g.isDefault }

    override fun getDefault(name: String?): GroupObject = objects.find { it.isDefault } ?: GroupObject.DEFAULT

    companion object : PublicProperty<GroupRegistry> by publicProperty("GroupRegistry") {
        fun createDefault() = GroupRegistry(mutableListOf(GroupObject.DEFAULT))
    }
}