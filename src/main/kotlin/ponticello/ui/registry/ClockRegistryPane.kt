package ponticello.ui.registry

import javafx.event.Event
import ponticello.model.player.ClockObject
import ponticello.model.registry.ClockRegistry

class ClockRegistryPane(clocks: ClockRegistry) : ObjectRegistryPane<ClockObject>(clocks) {
    init {
        setup()
    }

    override fun createNewObject(name: String, ev: Event?): ClockObject = ClockObject.withName(name)
}