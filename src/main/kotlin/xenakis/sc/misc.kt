package xenakis.sc

import hextant.codegen.Component
import hextant.codegen.Compound
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.model.registry.ObjectReference
import xenakis.model.score.ParameterControlList.NamedParameterControl
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.ParameterControlSelector

@Compound(nodeType = ScExpr::class)
data class ParameterReference(
    @Component(ParameterControlSelector::class)
    val parameter: ObjectReference<NamedParameterControl>,
) : ScExpr {
    override fun getLfo(): LFO = ParameterReferenceLFO(parameter)

    override fun code(writer: ScWriter, context: Context) {
        throw IllegalStateException("ParameterReference to ${parameter.name.now} should have been resolved.")
    }
}

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