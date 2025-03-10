package xenakis.model.flow

import reaktive.Observer
import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.BusControl
import xenakis.model.score.ParameterControl
import xenakis.model.score.ParameterControls
import xenakis.sc.BusControlSpec

class ControlsListener(
    private val obj: ParameterizedObject,
    private val wrapped: AudioNode.Listener
) : ParameterControls.Listener {
    private val observers = mutableMapOf<String, Observer>()

    override fun addedControl(parameter: String, control: ParameterControl) {
        val spec = obj.getSpec(parameter)
        if (spec is BusControlSpec && control is BusControl) {
            val bus = control.bus.now.get<BusObject>()
            wrapped.addedBus(bus, spec.flow)
            observers[parameter] = control.bus.observe { _, old, new ->
                wrapped.removedBus(old.get(), spec.flow)
                wrapped.addedBus(new.get(), spec.flow)
            }
        }
    }

    override fun removedControl(parameter: String, control: ParameterControl) {
        val spec = obj.getSpec(parameter)
        if (spec is BusControlSpec && control is BusControl) {
            val bus = control.bus.now.get<BusObject>()
            wrapped.removedBus(bus, spec.flow)
            observers.remove(parameter)
        }
    }
}