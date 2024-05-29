package xenakis.impl

import reaktive.event.event
import xenakis.impl.UDPSuperColliderClient.StatusUpdate

class StatusListener(private val monitor: ConsoleMonitor) : ConsoleMonitor.Listener {
    private val statusUpdate = event<StatusUpdate>()
    val statusUpdates get() = statusUpdate.stream

    var status: StatusUpdate = StatusUpdate.Starting
        set(value) {
            field = value
            statusUpdate.fire(value)
        }

    init {
        monitor.addListener(this)
    }

    fun remove() {
        monitor.removeListener(this)
    }

    override fun process(txt: String) {
        if ("-> OSCFunc(/eval, nil, nil, nil)" in txt) {
            status = StatusUpdate.ReadyToBoot
        } else if ("SuperCollider 3 server ready." in txt) {
            status = StatusUpdate.ServerBooted
        } else if ("Server 'localhost' exited" in txt) {
            status = StatusUpdate.ExitedServer
        }
    }
}