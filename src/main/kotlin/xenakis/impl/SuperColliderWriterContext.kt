package xenakis.impl

import java.io.StringWriter

class SuperColliderWriterContext(private val output: Appendable) : SuperColliderContext {
    private val scWriter get() = ScWriter(output)

    override fun postAsync(command: String) {
        output.appendLine(command)
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