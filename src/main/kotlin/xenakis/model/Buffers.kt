package xenakis.model

import kotlinx.serialization.Serializable
import xenakis.impl.SuperColliderContext
import xenakis.sc.Buffer
import xenakis.sc.FileBuffer

@Serializable
class Buffers(private val _buffers: MutableList<Buffer> = mutableListOf()) {
    val buffers: List<Buffer> get() = _buffers

    fun loadBuffers(context: SuperColliderContext) = context.postAsync {
        for (buf in buffers) {
            +"if(${buf.variableName} != nil) { ${buf.variableName}.free }"
            +buf.initializationCode
        }
    }

    fun addBuffer(buffer: Buffer, context: SuperColliderContext) {
        _buffers.add(buffer)
        context.postAsync(buffer.initializationCode)
    }

    fun removeBuffer(buffer: Buffer, context: SuperColliderContext) {
        _buffers.remove(buffer)
        context.postAsync {
            +"${buffer.variableName}.free"
            +"${buffer.variableName} = nil"
        }
    }

    fun reloadBuffer(buffer: Buffer, context: SuperColliderContext) {
        context.postAsync {
            +"${buffer.variableName}.free"
            +buffer.initializationCode
        }
    }

    fun renameBuffer(buffer: FileBuffer, new: String, context: SuperColliderContext) {
        context.postAsync {
            +"~buf_$new = ${buffer.variableName}"
            +"${buffer.variableName} = nil"
        }
        buffer.name = new
    }
}