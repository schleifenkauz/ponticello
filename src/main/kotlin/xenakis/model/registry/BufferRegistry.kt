package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.model.obj.BufferObject
import xenakis.model.obj.SuperColliderObject

@Serializable
class BufferRegistry(
    override val objects: MutableList<BufferObject> = mutableListOf(),
) : SuperColliderObjectRegistry<BufferObject>() {
    override val objectType: String
        get() = "Buffer"

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.ServerBoot

    override fun initialize(context: Context) {
        super.initialize(context)
        context[BufferRegistry] = this
    }

    companion object : PublicProperty<BufferRegistry> by publicProperty("BufferRegistry") {
        fun createDefault() = BufferRegistry()
    }
}