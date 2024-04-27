package xenakis.sc

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.ColorSerializer
import xenakis.impl.ScWriter

interface ParameterizedObject {
    val parameters: List<ParameterDef>

    fun getParameter(name: String): ParameterDef =
        parameters.find { p -> p.name == name } ?: error("no parameter named $name found in $this")
}

@Serializable
data class ParameterDef(
    var name: String,
    val spec: ControlSpec,
    @Serializable(with = ColorSerializer::class) var associatedColor: Color = Color.BLACK
) : ScElement {
    override fun code(writer: ScWriter) {
        writer.append(name)
        writer.append(" = ")
        "\\$name.${spec.code}"
    }

    companion object {
        val freq = ParameterDef("freq", NumericalControlSpec(440.0, 20.0, 20000.0, Warp.Exponential, 1.0))
        val amp = ParameterDef("amp", NumericalControlSpec(0.1, 0.0, 1.0, Warp.Linear, 0.001))
        val pan = ParameterDef("pan", NumericalControlSpec(0.0, -1.0, 1.0, Warp.Linear, 0.01))
    }
}

@Serializable
data class SynthDef(
    var name: String,
    var rate: Rate,
    override var parameters: MutableList<ParameterDef> = mutableListOf(),
    val variables: MutableList<Variable> = mutableListOf(),
    val body: MutableList<ScExpr> = mutableListOf(),
    @Serializable(with = ColorSerializer::class) var associatedColor: Color
) : ScElement, ParameterizedObject {
    override fun code(writer: ScWriter) {
        writer.append("SynthDef(\\$name, ")
        val statements = parameters.map { RawScExpr(it.code) } + body
        val graphFunc = ScFunction(emptyList(), CodeBlock(variables, statements))
        graphFunc.code(writer)
        writer.append(")")
    }

    companion object {
        val default = SynthDef(
            "default", Rate.Audio,
            mutableListOf(ParameterDef.freq, ParameterDef.amp, ParameterDef.pan),
            associatedColor = Color.WHITE
        )
    }
}