package ponticello.sc

import hextant.codegen.Component
import hextant.codegen.Compound
import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.model.registry.ObjectReference
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.sc.client.ScWriter
import ponticello.sc.editor.ParameterControlSelector
import reaktive.value.now

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
@Compound
data class EventDictionary(val entries: List<NamedExpr>)