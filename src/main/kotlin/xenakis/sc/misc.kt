package xenakis.sc

import hextant.codegen.Compound
import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.sc.client.ScWriter

@Serializable
data class VSTPlugin(
    val input: ScExpr,
    val channels: Int,
    val pluginName: String,
    val id: String,
    val presetName: String,
) : ScExpr {
    override val isValid: Boolean
        get() = input.isValid

    override val children: List<ScElement>
        get() = listOf(input)

    override fun code(writer: ScWriter, context: Context) {
        writer.append("VSTPlugin.ar(${input.code(context)}, $channels, id: '$id')")
    }
}

@Serializable
@Compound
data class EventDictionary(val entries: List<NamedExpr>)