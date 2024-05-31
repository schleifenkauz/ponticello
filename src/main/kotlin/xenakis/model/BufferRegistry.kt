package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.impl.SuperColliderContext

@Serializable
class BufferRegistry(
    private val _buffers: MutableList<BufferObject> = mutableListOf()
) : ObjectRegistry<BufferObject>() {
    val buffers: List<BufferObject> get() = _buffers

    override val objectType: String
        get() = "Buffer"

    override val objects: MutableList<BufferObject>
        get() = _buffers

    override fun initialize(context: Context) {
        super.initialize(context)
        context[BufferRegistry] = this
    }

    override fun getDefault(): BufferObject = NoBuffer

    override fun get(name: String) = if (name == "<none>") NoBuffer else super.get(name)

    fun SuperColliderContext.reloadBuffers() = run {
        for (buf in buffers) {
            +"if(${buf.variableName} != nil) { ${buf.variableName}.free }"
            +buf.initializationCode
        }
    }

    override fun onAdded(obj: BufferObject, idx: Int) {
        context[SuperColliderClient].run(obj.initializationCode)
    }

    override fun onRemoved(obj: BufferObject, idx: Int) {
        context[SuperColliderClient].run {
            +"${obj.variableName}.free"
            +"${obj.variableName} = nil"
        }
    }

    fun reloadBuffer(buffer: BufferObject, context: SuperColliderContext) {
        context.run {
            +"${buffer.variableName}.free"
            +buffer.initializationCode
        }
    }

    fun hasBuffer(name: String): Boolean = _buffers.any { b -> b.name.now == name }

    interface View : ObjectRegistry.View<BufferObject>

    companion object : PublicProperty<BufferRegistry> by publicProperty("BufferRegistry")
}