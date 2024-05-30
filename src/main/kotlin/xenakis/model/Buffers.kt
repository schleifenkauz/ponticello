package xenakis.model

import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.SuperColliderContext
import xenakis.sc.Buffer

@Serializable
class Buffers(private val _buffers: MutableList<Buffer> = mutableListOf()) {
    val buffers: List<Buffer> get() = _buffers

    fun SuperColliderContext.loadBuffers() = run {
        for (buf in buffers) {
            +"if(${buf.variableName} != nil) { ${buf.variableName}.free }"
            +buf.initializationCode
        }
    }

    fun addBuffer(buffer: Buffer, context: SuperColliderContext) {
        _buffers.add(buffer)
        context.run(buffer.initializationCode)
    }

    fun removeBuffer(buffer: Buffer, context: SuperColliderContext) {
        _buffers.remove(buffer)
        context.run {
            +"${buffer.variableName}.free"
            +"${buffer.variableName} = nil"
        }
    }

    fun reloadBuffer(buffer: Buffer, context: SuperColliderContext) {
        context.run {
            +"${buffer.variableName}.free"
            +buffer.initializationCode
        }
    }

    fun renameBuffer(buffer: Buffer, new: String, context: SuperColliderContext) {
        context.run {
        }
        buffer.rename(new)
    }

    fun hasBuffer(name: String): Boolean = _buffers.any { b -> b.name.now == name }
}