package xenakis.model.obj

import reaktive.value.now
import xenakis.model.flow.FlowType
import xenakis.model.registry.NamedObject
import xenakis.model.score.BusControl
import xenakis.model.score.ParameterControls
import xenakis.sc.BusControlSpec
import xenakis.sc.ControlSpec

interface ParameterizedObject : NamedObject {
    val def: ParameterizedObjectDef
    val controls: ParameterControls

    val parameters
        get() = controls.extraParameters + def.parameters.now
            .filter { param -> controls.getExtraSpec(param.name.now) == null }

    fun getSpec(parameter: String): ControlSpec? =
        controls.getExtraSpec(parameter) ?: def.getParameter(parameter)?.spec?.now

    fun getInputs(): Collection<BusObject> = getConnectedBusses(FlowType.In)

    fun getOutputs(): Collection<BusObject> = getConnectedBusses(FlowType.Out)

    private fun getConnectedBusses(flowType: FlowType): Set<BusObject> = buildSet {
        for (parameter in parameters) {
            val spec = parameter.spec.now
            val name = parameter.name.now
            if (spec !is BusControlSpec) continue
            if (spec.flow != flowType) {
                val ctrl = controls.controlMap[name] as? BusControl ?: continue
                add(ctrl.bus.now.get())
            }
        }
    }

    fun addControlsForAllObjectParameters() {
        for (param in def.parameters.now) {
            val name = param.name.now
            if (name !in controls.controlMap) {
                controls.addControl(name, param.defaultControl(context))
            }
        }
    }
}