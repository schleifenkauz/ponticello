package xenakis.ui.midi

import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry

class ControlBusesMidiReceiver(private val buses: BusRegistry) : AbstractMidiContext(buses.context) {
    override fun cc(channel: Int, index: Int, value: Int) {
        val controlBuses = buses
            .filterIsInstance<BusObject.ControlBus>()
            .filter { b -> b.spec.now != null }
        if (index !in controlBuses.indices) return
        val bus = controlBuses[index]
        val spec = bus.spec.now!!
        val newValue = adjustValue(spec.defaultValue.get(), spec, value)
        bus.setDefaultValue(newValue)
    }
}