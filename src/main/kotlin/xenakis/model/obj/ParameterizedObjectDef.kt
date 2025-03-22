package xenakis.model.obj

import hextant.context.Context
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.model.score.ParameterControl
import xenakis.sc.ControlSpec

interface ParameterizedObjectDef {
    val parameters: List<ParameterDefObject>

    fun getParameter(name: String): ParameterDefObject? = parameters.find { it.name.now == name }

    fun getSpec(name: String): ReactiveVariable<ControlSpec>? = getParameter(name)?.spec

    fun setSpec(parameterName: String, spec: ControlSpec) {
        val parameter = getParameter(parameterName) ?: error("Parameter $parameterName not found in $this")
        parameter.spec.now = spec
    }

    fun hasParameter(name: String): Boolean = parameters.any { it.name.now == name }

    fun defaultControls(
        context: Context, defaultGroup: GroupReference?, defaultBus: BusReference?
    ): MutableList<Pair<String, ParameterControl>> {
        val controls = parameters.mapTo(mutableListOf()) { p ->
            p.name.now to p.defaultControl(context, defaultBus)
        }
        return controls
    }
}