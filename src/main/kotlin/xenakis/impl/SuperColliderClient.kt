package xenakis.impl

import bundles.PublicProperty
import bundles.publicProperty
import com.illposed.osc.OSCMessage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

interface SuperColliderClient : SuperColliderContext {
    val statusListener: StatusListener

    fun sendAsync(address: String, arguments: List<Any>)

    fun send(address: String, arguments: List<Any>): CompletableFuture<OSCMessage>

    fun eval(code: String): CompletableFuture<String> {
        logger.info("eval: $code")
        return send("eval", listOf(code))
            .orTimeout(10000, TimeUnit.MILLISECONDS)
            .thenApply { msg ->
                val result = msg.arguments[1] as String
                logger.info("evaluating $code returned $result")
                result
            }
    }

    override fun run(command: String) {
        logger.info("run: $command")
        sendAsync("run", listOf(command))
    }

    override fun run(writeCode: ScWriter.() -> Unit) {
        val command = code(writeCode)
        if (command.isNotBlank()) run(command)
    }

    fun quit()

    companion object : PublicProperty<SuperColliderClient> by publicProperty("SuperColliderClient") {
        private val logger: Logger = Logger.getLogger("SuperColliderClient")
    }

    val consoleMonitor: ConsoleMonitor
}