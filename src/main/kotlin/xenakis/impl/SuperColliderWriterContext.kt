package xenakis.impl

import java.io.StringWriter
import java.io.Writer

class SuperColliderWriterContext(private val writer: Writer) : SuperColliderContext {
    private val scWriter = ScWriter(writer)

    override fun postAsync(command: String) {
        writer.appendLine(command)
    }

    override fun postAsync(writeCode: ScWriter.() -> Unit) {
        scWriter.writeCode()
    }

    companion object {
        inline fun wrap(context: SuperColliderContext, block: SuperColliderWriterContext.() -> Unit) {
            if (context is SuperColliderWriterContext) return context.block()
            val writer = StringWriter()
            val wrapper = SuperColliderWriterContext(writer)
            wrapper.block()
            context.postAsync(writer.toString())
        }
    }
}