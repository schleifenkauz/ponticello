package xenakis.model.obj

import reaktive.value.now
import xenakis.model.flow.FlowType
import xenakis.model.registry.NamedObject
import xenakis.model.score.BusControl
import xenakis.model.score.ParameterControlList
import xenakis.sc.BusControlSpec
import xenakis.sc.ControlSpec

interface ParameterizedObject : NamedObject {
    val def: ParameterizedObjectDef
    val controls: ParameterControlList

    fun getSpec(parameter: String): ControlSpec? = controls.getOrNull(parameter)?.spec?.now

    fun getInputs(): Collection<BusObject> = getConnectedBusses(FlowType.In)

    fun getOutputs(): Collection<BusObject> = getConnectedBusses(FlowType.Out)

    private fun getConnectedBusses(flowType: FlowType): Set<BusObject> = buildSet {
        for (ctrl in controls.all()) {
            val spec = ctrl.spec.now
            val value = ctrl.now
            if (value is BusControl || spec !is BusControlSpec) continue
            if (spec.flow != flowType) {
                val ctrl = ctrl.now as? BusControl ?: continue
                val bus = ctrl.bus.now.get()
                if (bus != null) {
                    add(bus)
                }
            }
        }
    }

    fun addControlsForAllObjectParameters() {
        for (param in def.parameters) {
            val name = param.name.now
            if (name !in controls.controlMap) {
                controls.addControl(name, param.defaultControl(context))
            }
        }
    }
}