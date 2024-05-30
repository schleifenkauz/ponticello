package xenakis.sc

import hextant.codegen.Component
import hextant.codegen.Compound
import hextant.codegen.ListEditor
import hextant.core.editor.ColorEditor
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.ColorSerializer
import xenakis.impl.ScWriter
import xenakis.sc.editor.ParameterDefExpander

interface ParameterizedObject {
    val parameters: List<ParameterDef>

    fun getParameter(name: String): ParameterDef =
        parameters.find { p -> p.name.text == name } ?: error("no parameter named $name found in $this")
}

@Serializable
@Compound(serializable = true)
@ListEditor(serializable = true, editorCls = ParameterDefExpander::class)
data class ParameterDef(val name: Identifier, val spec: ControlSpec) {
    companion object {
        val freq = ParameterDef(
            Identifier("freq"),
            NumericalControlSpec(440.0, 20.0, 20000.0, Warp.Exponential, 1.0, Color.WHITE)
        )
        val amp = ParameterDef(
            Identifier("amp"),
            NumericalControlSpec(0.1, 0.0, 1.0, Warp.Linear, 0.01, Color.ORANGE)
        )
        val pan = ParameterDef(
            Identifier("pan"),
            NumericalControlSpec(0.0, -1.0, 1.0, Warp.Linear, 0.1, Color.BLUE)
        )

        val defaults = listOf(freq, amp, pan)
    }
}

@Serializable
@Compound(serializable = true)
@ListEditor(serializable = true)
class SynthDef(
    val name: Identifier,
    override val parameters: List<ParameterDef>, val ugenGraph: CodeBlock,
    @Component(ColorEditor::class) @Serializable(with = ColorSerializer::class) val associatedColor: Color
) : ScElement, ParameterizedObject {
    override fun code(writer: ScWriter) {
        writer.append("SynthDef(\\${name.text}, ")
        val extraVariables = parameters.map { p -> Variable(p.name, RawScExpr("\\${p.name.text}.${p.spec.code}")) }
        val block = ugenGraph.copy(variables = ugenGraph.variables + extraVariables)
        val graphFunc = ScFunction(emptyList(), block)
        graphFunc.code(writer)
        writer.append(")")
    }

    companion object {
        val default = SynthDef(
            Identifier("default"),
            parameters = listOf(ParameterDef.freq, ParameterDef.amp, ParameterDef.pan),
            ugenGraph = CodeBlock(emptyList(), emptyList()),
            associatedColor = Color.WHITE
        )
    }
}