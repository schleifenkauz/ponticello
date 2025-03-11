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
    private val _buffers: MutableList<BufferObject> = mutableListOf()
) : SuperColliderObjectRegistry<BufferObject>() {
    val buffers: List<BufferObject> get() = _buffers

    override val objectType: String
        get() = "Buffer"

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.ServerBoot

    override val objects: MutableList<BufferObject>
        get() = _buffers

    override fun initialize(context: Context) {
        super.initialize(context)
        context[BufferRegistry] = this
    }

    override fun getDefault(name: String?): BufferObject = error("No default buffer")

    interface Listener : ObjectRegistry.Listener<BufferObject>

    companion object : PublicProperty<BufferRegistry> by publicProperty("BufferRegistry")
}