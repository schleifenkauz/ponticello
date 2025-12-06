package ponticello.model.score.controls

import ponticello.sc.client.ScWriter

sealed class CodegenContext {
    protected abstract val writer: ScWriter

    val processVar: String get() = "proc"

    fun getControlBus(parameter: String) = "proc.getBus($parameter)"

    fun registerControlBus(parameter: String, initialValue: String): String {
        writer.appendLine("proc.registerBus($parameter, $initialValue);")
        return getControlBus(parameter)
    }

    fun subArg(): CodegenContext = SubArg(writer)

    data class Synth(override val writer: ScWriter) : CodegenContext() {
        val synthVar: String get() = "synth"
    }

    data class Process(override val writer: ScWriter) : CodegenContext() {
        fun getArgument(parameter: String) = "proc.env[\\$parameter]"

        fun assignArgument(parameter: String, expr: String) {
            writer.appendLine("proc.env[\\$parameter] = $expr;")
        }
    }

    data class SubArg(override val writer: ScWriter) : CodegenContext()
}