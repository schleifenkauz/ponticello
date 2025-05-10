package ponticello.model.obj

import hextant.context.Context
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.NamedObject
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.ControlSpec
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveVariable
import reaktive.value.now

interface ParameterizedObjectDef : NamedObject {
    val parameters: ParameterDefList

    fun allParameters(): List<ParameterDefObject> = parameters

    fun getParameter(name: String): ParameterDefObject? = allParameters().find { p -> p.name.now == name }

    fun getSpec(name: String): ReactiveVariable<ControlSpec>? = getParameter(name)?.spec

    fun setSpec(parameterName: String, spec: ControlSpec) {
        val parameter = getParameter(parameterName) ?: error("Parameter $parameterName not found in $this")
        parameter.spec.now = spec
    }

    fun hasParameter(name: String): Boolean = allParameters().any { it.name.now == name }

    fun defaultControls(
        context: Context, defaultBus: BusReference?,
    ): MutableList<Pair<String, ParameterControl>> = allParameters().mapTo(mutableListOf()) { p ->
        p.name.now to p.defaultControl(defaultBus)
    }

    fun getDefaultControls(associatedObject: ScoreObjectGroup?): ParameterControlList {
        val defaultBus = associatedObject?.defaultBusRef?.now ?: context[BusRegistry].getDefault().reference()
        val controls = defaultControls(context, defaultBus)
        return ParameterControlList.from(controls)
    }

    fun sync()
}