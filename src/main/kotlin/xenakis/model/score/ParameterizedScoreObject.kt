package xenakis.model.score

import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.sc.ControlSpec

@Serializable
sealed class ParameterizedScoreObject : ScoreObject() {
    abstract val def: ParameterizedObjectDef
    abstract val controls: ParameterControls

    val parameters
        get() = controls.extraParameters + def.parameters.now
            .filter { param -> controls.getExtraSpec(param.name.now) == null }

    override val associatedControls: Map<String, ParameterControl> get() = controls.controlMap

    override fun getSpec(parameter: String): ControlSpec? =
        controls.getExtraSpec(parameter) ?: def.getParameter(parameter)?.spec?.now
}