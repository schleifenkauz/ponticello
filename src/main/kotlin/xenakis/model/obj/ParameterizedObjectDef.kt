package xenakis.model.obj

import hextant.context.Context
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.NamedObject
import xenakis.model.registry.reference
import xenakis.model.score.ParameterControlList
import xenakis.model.score.ScoreObjectGroup
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.ControlSpec
import xenakis.ui.registry.ParameterDefList

interface ParameterizedObjectDef : NamedObject {
    val parameters: ParameterDefList

    fun getParameter(name: String): ParameterDefObject? = parameters.find { it.name.now == name }

    fun getSpec(name: String): ReactiveVariable<ControlSpec>? = getParameter(name)?.spec

    fun setSpec(parameterName: String, spec: ControlSpec) {
        val parameter = getParameter(parameterName) ?: error("Parameter $parameterName not found in $this")
        parameter.spec.now = spec
    }

    fun hasParameter(name: String): Boolean = parameters.any { it.name.now == name }

    fun defaultControls(
        context: Context, defaultGroup: GroupReference?, defaultBus: BusReference?,
    ): MutableList<Pair<String, ParameterControl>> {
        val controls = parameters.mapTo(mutableListOf()) { p ->
            p.name.now to p.defaultControl(context, defaultBus, defaultGroup)
        }
        return controls
    }

    fun getDefaultControls(associatedObject: ScoreObjectGroup?): ParameterControlList {
        val defaultGroup = associatedObject?.defaultGroupRef?.now ?: context[GroupRegistry].getDefault().reference()
        val defaultBus = associatedObject?.defaultBusRef?.now ?: context[BusRegistry].getDefault().reference()
        val controls = defaultControls(context, defaultGroup, defaultBus)
        return ParameterControlList.from(controls)
    }

    fun sync()
}