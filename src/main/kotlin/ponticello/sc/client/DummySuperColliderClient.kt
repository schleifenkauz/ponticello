package ponticello.sc.client

import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.argument.OSCTimeTag64
import hextant.context.Context
import reaktive.Observer
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CompletableFuture

class DummySuperColliderClient(
    override val context: Context,
    override val sampleRate: Double
) : SuperColliderClient {
    private val outputStream = PipedOutputStream()
    private val inputStream = PipedInputStream(outputStream)

    override val consoleMonitor: ConsoleMonitor = ConsoleMonitor(inputStream)

    override val serverReceiver: OSCReceiver? get() = null

    override fun onClientReady(action: () -> Unit) {
        action()
    }

    override fun onServerBooted(action: () -> Unit): Observer {
        action()
        return Observer.nothing
    }

    override fun onTreeCleared(initially: Boolean, action: () -> Unit) {
    }

    override fun send(
        address: String,
        arguments: List<Any>,
        description: String?
    ): CompletableFuture<String> = CompletableFuture.completedFuture("dummy")

    override fun sendAsync(address: String, arguments: List<Any>) {
    }

    override fun addListener(listener: OSCMessageListener) {
    }

    override fun addListener(
        address: String,
        listener: (time: OSCTimeTag64, msg: OSCMessage) -> Unit
    ) {
    }

    override fun quit() {
    }

    override fun run(command: String) {
    }
}