package xenakis.sc.client

class StatusListener(private val monitor: ConsoleMonitor) : ConsoleMonitor.Listener {
    private val handlers = mutableMapOf<StatusUpdate, MutableList<() -> Unit>>()

    private fun handlers(update: StatusUpdate): MutableList<() -> Unit> = handlers.getOrPut(update) { mutableListOf() }

    var status: StatusUpdate = StatusUpdate.Starting
        set(value) {
            if (field == value) return
            field = value
            for (handler in handlers(value)) {
                handler.invoke()
            }
        }

    init {
        monitor.addListener(this)
    }

    fun remove() {
        monitor.removeListener(this)
    }

    override fun process(txt: String) {
        when {
            "Welcome to SuperCollider" in txt -> status = StatusUpdate.ScLangBooted
            "SuperCollider 3 server ready." in txt -> status = StatusUpdate.ServerBooted
            "Successfully setup OSC!" in txt -> status = StatusUpdate.OSCReady
            "Server 'localhost' exited" in txt -> status = StatusUpdate.ExitedServer
        }
    }

    fun on(update: StatusUpdate, handler: () -> Unit) {
        if (status == update) handler()
        handlers(update).add(handler)
    }

    enum class StatusUpdate {
        Starting,
        ScLangBooted,
        OSCReady,
        ServerBooted,
        ExitedServer,
        Exited
    }
}