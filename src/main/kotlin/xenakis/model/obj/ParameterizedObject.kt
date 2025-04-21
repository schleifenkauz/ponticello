package xenakis.model.obj

import reaktive.value.ReactiveValue
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.model.flow.FlowType
import xenakis.model.player.ActiveObject
import xenakis.model.registry.NamedObject
import xenakis.model.score.ParameterControlList
import xenakis.model.score.controls.BusControl
import xenakis.sc.BusControlSpec
import xenakis.sc.ControlSpec

interface ParameterizedObject : NamedObject {
    val def: ParameterizedObjectDef
    val controls: ParameterControlList

    val superColliderPrefix: String

    fun activeObjects(): List<ActiveObject>

    fun duration(): ReactiveValue<Decimal>?

    fun getSpec(parameter: String): ControlSpec? = controls.getOrNull(parameter)?.spec?.now

    fun getInputs(): Collection<BusObject> = getConnectedBusses(FlowType.In)

    fun getOutputs(): Collection<BusObject> = getConnectedBusses(FlowType.Out)

    private fun getConnectedBusses(flowType: FlowType): Set<BusObject> = buildSet {
        for (control in controls.all()) {
            val spec = control.spec.now
            val value = control.now
            if (value is BusControl || spec !is BusControlSpec) continue
            if (spec.flow != flowType) {
                val ctrl = control.now as? BusControl ?: continue
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

    fun validate(): Boolean
}