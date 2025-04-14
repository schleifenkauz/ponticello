package xenakis.model.flow

import reaktive.Observer
import reaktive.value.now
import xenakis.model.score.ParameterControlList
import xenakis.model.score.ParameterControlList.NamedParameterControl
import xenakis.model.score.controls.BusControl
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.BusControlSpec
import xenakis.sc.ControlSpec

class AudioNodeBusControlsListener(private val wrapped: AudioNode.Listener) : ParameterControlList.Listener {
    private val observers = mutableMapOf<BusControl, Observer>()

    override fun added(control: NamedParameterControl, idx: Int) {
        val spec = control.spec.now ?: return
        val ctrl = control.now
        if (spec is BusControlSpec && ctrl is BusControl) {
            addedBusControl(ctrl, spec.flow)
        }
    }

    override fun removed(control: NamedParameterControl) {
        val spec = control.spec.now ?: return
        val ctrl = control.now
        if (spec is BusControlSpec && ctrl is BusControl) {
            removedBusControl(ctrl, spec.flow)
        }
    }

    private fun addedBusControl(ctrl: BusControl, flow: FlowType) {
        val bus = ctrl.bus.now.get()
        if (bus != null) wrapped.addedBus(bus, flow)
        observers[ctrl] = observe(ctrl, flow)
    }

    private fun observe(ctrl: BusControl, flow: FlowType): Observer = ctrl.bus.observe { _, old, new ->
        old.get()?.let { b -> wrapped.removedBus(b, flow) }
        new.get()?.let { b -> wrapped.addedBus(b, flow) }
    }

    private fun removedBusControl(ctrl: BusControl, flow: FlowType) {
        val bus = ctrl.bus.now.get()
        if (bus != null) wrapped.removedBus(bus, flow)
        observers.remove(ctrl)!!.kill()
    }

    override fun reassignedControl(
        namedControl: NamedParameterControl,
        oldControl: ParameterControl,
        control: ParameterControl
    ) {
        val spec = namedControl.spec.now
        if (oldControl is BusControl && spec is BusControlSpec) removedBusControl(oldControl, spec.flow)
        if (control is BusControl && spec is BusControlSpec) addedBusControl(control, spec.flow)
    }

    override fun changedSpec(control: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        val ctrl = control.now
        if (ctrl !is BusControl) return
        if (oldSpec is BusControlSpec) removedBusControl(ctrl, oldSpec.flow)
        if (newSpec is BusControlSpec) addedBusControl(ctrl, newSpec.flow)
    }
}