package xenakis.impl

import bundles.PublicProperty
import bundles.publicProperty
import com.illposed.osc.OSCMessage
import xenakis.model.Logger
import xenakis.model.Logger.Category
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

interface SuperColliderClient : SuperColliderContext {
    val statusListener: StatusListener

    fun sendAsync(address: String, arguments: List<Any>)

    fun send(address: String, arguments: List<Any> = emptyList()): CompletableFuture<OSCMessage>

    fun eval(code: String): CompletableFuture<String> {
        if (isMyComputerDumb) {
            run(code)
            return CompletableFuture.completedFuture("")
        }
        Logger.fine("eval $code", Category.SuperCollider, detailMessage = code)
        return send("eval", listOf(code))
            .orTimeout(10000, TimeUnit.MILLISECONDS)
            .thenApply { msg ->
                val result = msg.arguments[1] as String
                Logger.fine("evaluating $code returned ${result.take(20)}", Category.SuperCollider, result)
                result
            }
    }

    override fun run(command: String) {
        if (command == "(\n)\n") return
        Logger.fine("run: $command", Category.SuperCollider)
        sendAsync("run", listOf(command))
    }

    override fun run(writeCode: ScWriter.() -> Unit) {
        val command = code(writeCode)
        if (command.isNotBlank()) run(command)
    }

    fun quit()

    companion object : PublicProperty<SuperColliderClient> by publicProperty("SuperColliderClient")

    val consoleMonitor: ConsoleMonitor
}