package xenakis.ui.registry

import javafx.event.Event
import xenakis.model.player.ClockObject
import xenakis.model.registry.ClockRegistry

class ClockRegistryPane(clocks: ClockRegistry) : ObjectRegistryPane<ClockObject>(clocks) {
    init {
        setup()
    }

    override fun createNewObject(name: String, ev: Event?): ClockObject = ClockObject.withName(name)
}