package xenakis.model.score

import kotlinx.serialization.Serializable
import xenakis.model.obj.ParameterizedObject
import xenakis.sc.ControlSpec

@Serializable
sealed class ParameterizedScoreObject : ScoreObject(), ParameterizedObject {
    override val associatedControls: Map<String, ParameterControl> get() = controls.controlMap
    override fun getSpec(parameter: String): ControlSpec? = super<ParameterizedObject>.getSpec(parameter)
}