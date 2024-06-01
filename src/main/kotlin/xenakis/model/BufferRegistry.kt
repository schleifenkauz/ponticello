package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.impl.SuperColliderContext
import java.io.File

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

    override fun getDefault(): BufferObject = BufferObject.defaultBuffer

    override fun get(name: String) = if (name == "0") BufferObject.defaultBuffer else super.get(name)

    fun hasFile(file: File) = objects.any { o -> o is FileBuffer && o.referencedFile.now == file }

    override fun add(obj: BufferObject, idx: Int) {
        if (obj is FileBuffer && hasFile(obj.referencedFile.now)) return
        super.add(obj, idx)
    }

    fun SuperColliderContext.initializeBuffers() = run {
        for (buf in buffers) {
            +buf.initializationCode
        }
    }

    fun sync() {
        val client = context[SuperColliderClient]
        for (buf in buffers) {
            buf.sync(client)
        }
    }

    override fun onRemoved(obj: BufferObject, idx: Int) {
        obj.onRemove()
    }

    fun reloadBuffer(buffer: BufferObject, context: SuperColliderContext) {
        context.run {
            +"${buffer.variableName}.free"
            +buffer.initializationCode
        }
    }

    interface View : ObjectRegistry.View<BufferObject>

    companion object : PublicProperty<BufferRegistry> by publicProperty("BufferRegistry")
}