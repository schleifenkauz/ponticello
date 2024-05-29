package xenakis.impl

import bundles.PublicProperty
import bundles.publicProperty
import com.illposed.osc.OSCMessage
import java.util.concurrent.CompletableFuture

interface SuperColliderClient : SuperColliderContext {
    val statusListener: StatusListener

    fun sendAsync(address: String, arguments: List<Any>)

    fun send(address: String, arguments: List<Any>): CompletableFuture<OSCMessage>

    fun eval(code: String): CompletableFuture<String> =
        send("/eval", listOf(code)).thenApply { msg ->
            msg.arguments[0] as String
        }

    override fun run(command: String) {
        sendAsync("/run", listOf(command))
    }

    fun quit()

    companion object : PublicProperty<SuperColliderClient> by publicProperty("SuperColliderClient")

    val consoleMonitor: ConsoleMonitor
}