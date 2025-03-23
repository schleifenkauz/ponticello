package xenakis.model.player

import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.now
import reaktive.value.observe
import xenakis.impl.Decimal
import xenakis.model.obj.BusReference
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

    override fun added(control: ParameterControls.NamedParameterControl, idx: Int) {
        val parameter = control.name.now
        val ctrl = control.now
        addedControl(parameter, ctrl)
    }

    private fun addedControl(parameter: String, ctrl: ParameterControl) {
        observeControl(parameter, ctrl)
        when (ctrl) {
            is BusControl -> setBus(parameter, ctrl.bus.now)
            is BusValueControl -> mapBus(parameter, ctrl.bus.now)
            is ValueControl -> setValue(parameter, ctrl.value.now)
            else -> {} //no realtime updates possible
        }
    }

    private fun observeControl(parameter: String, control: ParameterControl) {
        when (control) {
            is BusControl -> controlObservers[control] = control.bus.observe { _, bus -> setBus(parameter, bus) }
            is BusValueControl -> controlObservers[control] = control.bus.observe { _, bus -> mapBus(parameter, bus) }
            is ValueControl -> controlObservers[control] =
                control.value.observe { _, value -> setValue(parameter, value) }

            else -> {} //no realtime updates possible
        }
    }

    private fun setValue(parameter: String, value: Decimal) {
        runOnActiveSynths { +"set('$parameter', $value)" }
    }

    private fun mapBus(parameter: String, bus: BusReference) {
        val superColliderName = bus.get()?.superColliderName ?: return
        runOnActiveSynths { +"map('$parameter', $superColliderName)" }
    }

    private fun setBus(parameter: String, bus: BusReference) {
        val superColliderName = bus.get()?.superColliderName ?: return
        runOnActiveSynths { +"set('$parameter', $superColliderName)" }
    }

    override fun removed(control: ParameterControls.NamedParameterControl) {
        controlObservers.remove(control.now)?.kill()
    }

    override fun reassignedControl(
        namedControl: ParameterControls.NamedParameterControl,
        oldControl: ParameterControl,
        control: ParameterControl
    ) {
        controlObservers.remove(oldControl)?.kill()
        addedControl(namedControl.name.now, control)
    }

    fun listen(controls: ParameterControls) {
        controls.addListener(this, initialize = false)
        for ((param, control) in controls.controlMap) {
            observeControl(param, control)
        }
    }
}