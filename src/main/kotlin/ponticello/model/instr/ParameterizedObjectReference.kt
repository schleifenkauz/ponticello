package ponticello.model.instr

import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.model.obj.FlowReference
import ponticello.model.obj.ScoreObjectReference
import ponticello.model.obj.resolve
import ponticello.model.registry.ScoreObjectRegistry

@Serializable
sealed class ParameterizedObjectReference : java.io.Serializable {
    @Serializable
    data class Flow(val reference: FlowReference) : ParameterizedObjectReference()

    @Serializable
    data class ScoreObject(val reference: ScoreObjectReference) : ParameterizedObjectReference()

    fun resolve(context: Context): ParameterizedObject? = when (this) {
        is Flow -> reference.resolve(context)
        is ScoreObject -> {
            reference.resolve(context[ScoreObjectRegistry])
        }
    } as ParameterizedObject?

    fun get(): ParameterizedObject? = when (this) {
        is Flow -> reference.get()
        is ScoreObject -> reference.get()
    } as ParameterizedObject?
}