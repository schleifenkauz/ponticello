package ponticello.ui.registry

import fxutils.prompt.IntegerPrompt
import javafx.event.Event
import ponticello.model.obj.BusObject
import ponticello.model.registry.BusRegistry

class AudioBusRegistryPane(busses: BusRegistry) : AbstractBusRegistryPane(busses) {
    init {
        setup()
    }

    override fun filter(obj: BusObject): Boolean = obj is BusObject.AudioBus

    override fun createNewObject(name: String, ev: Event?): BusObject? {
        val channels = IntegerPrompt("Channels", initialValue = 2, range = 1..16)
            .showDialog(ev) ?: return null
        return BusObject.audio(name, channels)
    }
}