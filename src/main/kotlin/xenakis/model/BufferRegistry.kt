package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import java.io.File

@Serializable
class BufferRegistry(
    private val _buffers: MutableList<BufferObject> = mutableListOf()
) : SuperColliderObjectRegistry<BufferObject>() {
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

    override fun getOrNull(name: String) = if (name == "0") BufferObject.defaultBuffer else super.getOrNull(name)

    fun getBufferFor(file: File): BufferObject? = objects.find { o -> o is FileBuffer && o.referencedFile.now == file }

    override fun add(obj: BufferObject, idx: Int) {
        if (obj is FileBuffer && getBufferFor(obj.referencedFile.now) != null) return
        super.add(obj, idx)
    }

    interface View : ObjectRegistry.View<BufferObject>

    companion object : PublicProperty<BufferRegistry> by publicProperty("BufferRegistry")
}