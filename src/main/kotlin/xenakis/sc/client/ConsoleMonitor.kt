package xenakis.sc.client

class ConsoleMonitor(private val process: Process) : Thread() {
    private var consoleMonitors = mutableListOf<Listener>()
    private val consoleUntilNow = StringBuilder()

    init {
        isDaemon = true
        name = "ConsoleMonitor"
    }

    fun addListener(listener: Listener) {
        listener.process(consoleUntilNow.toString())
        consoleMonitors.add(listener)
    }

    fun removeListener(listener: Listener) {
        consoleMonitors.remove(listener)
    }

    override fun run() {
        val stream = process.inputStream.reader()
        stream.useLines { lines ->
            for (line in lines) {
                if (interrupted()) break
                synchronized(this) {
                    consoleMonitors.toList().forEach { m -> m.process(line + "\n") }
                    consoleUntilNow.appendLine(line)
                }
            }
        }
    }

    fun interface Listener {
        fun process(txt: String)
    }

    object PipeToSystemOut : Listener {
        override fun process(txt: String) {
            print(txt)
        }
    }
}