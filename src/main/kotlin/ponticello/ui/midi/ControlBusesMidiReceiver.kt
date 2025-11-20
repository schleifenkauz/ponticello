package ponticello.ui.midi

import ponticello.model.instr.BusObject
import ponticello.model.server.BusRegistry
import reaktive.value.now

class ControlBusesMidiReceiver(private val buses: BusRegistry) : AbstractMidiContext(buses.context) {
    override fun cc(channel: Int, index: Int, value: Int) {
        val controlBuses = buses
            .filterIsInstance<BusObject.ControlBus>()
            .filter { b -> b.spec.now != null }
        if (index !in controlBuses.indices) return
        val bus = controlBuses[index]
        val spec = bus.spec.now!!
        val newValue = spec.defaultValue.get().adjustByMidiDelta(value, spec, context)
        bus.setDefaultValue(newValue)
    }
}