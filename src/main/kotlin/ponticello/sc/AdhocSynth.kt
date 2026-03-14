package ponticello.sc

import hextant.codegen.Compound
import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.sc.client.ScWriter

@Compound(nodeType = ScExpr::class)
@Serializable
data class AdhocSynth(
    val name: Identifier,
    val block: CodeBlock,
) : ScExpr {
    override val isValid: Boolean
        get() = block.isValid

    override val children: List<ScElement>
        get() = listOf(block)

    override fun code(writer: ScWriter, context: Context) = with(writer) {
        appendBlock("${"~adhoc_${name.text}"} = SynthDef(\\${name.text})", endLine = null) {
            block.writeCode(this.writer, context)
        }
        +".add.play(${"s.defaultGroup"}, addAction: ${
            "addToHead"
        })"
    }
}