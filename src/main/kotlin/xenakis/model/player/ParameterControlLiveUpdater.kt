package xenakis.model.player

import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.now
import reaktive.value.observe
import xenakis.impl.Decimal
import xenakis.model.obj.BusObject
import xenakis.model.registry.ObjectReference
import xenakis.model.score.*
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient

class ParameterControlLiveUpdater(
    private val client: SuperColliderClient,
    private val activeSynths: () -> List<String>
) : ParameterControls.Listener {
    @Transient
    private val controlObservers = mutableMapOf<ParameterControl, Observer>()

    private fun runOnActiveSynths(action: ScWriter.() -> Unit) {
        client.run {
            for (name in activeSynths()) {
                appendBlock("if ($name != nil && $name.isRunning)") {
                    append("$name.")
                    action()
                }
            }
        }
    }

    override fun addedControl(parameter: String, control: ParameterControl) {
        observeControl(parameter, control)
        when (control) {
            is BusControl -> setBus(parameter, control.bus.now)
            is BusValueControl -> mapBus(parameter, control.bus.now)
            is ConstantControl -> setValue(parameter, control.value.now)
            is KnobControl -> setValue(parameter, control.value.now)
            else -> {} //no realtime updates possible
        }
    }

    private fun observeControl(parameter: String, control: ParameterControl) {
        when (control) {
            is BusControl -> controlObservers[control] = control.bus.observe { _, bus -> setBus(parameter, bus) }
            is BusValueControl -> controlObservers[control] = control.bus.observe { _, bus -> mapBus(parameter, bus) }
            is ConstantControl -> controlObservers[control] =
                control.value.observe { _, value -> setValue(parameter, value) }

            is KnobControl -> controlObservers[control] =
                control.value.observe { _, value -> setValue(parameter, value) }

            else -> {} //no realtime updates possible
        }
    }

    private fun setValue(parameter: String, value: Decimal) {
        runOnActiveSynths { +"set('$parameter', $value)" }
    }

    private fun mapBus(parameter: String, bus: ObjectReference) {
        runOnActiveSynths { +"map('$parameter', ${bus.get<BusObject>().superColliderName})" }
    }

    private fun setBus(parameter: String, bus: ObjectReference) {
        runOnActiveSynths { +"set('$parameter', ${bus.get<BusObject>().superColliderName})" }
    }

    override fun removedControl(parameter: String, control: ParameterControl) {
        controlObservers.remove(control)?.kill()
    }

    fun listen(controls: ParameterControls) {
        controls.addListener(this, initialize = false)
        for ((param, control) in controls.controlMap) {
            observeControl(param, control)
        }
    }
}