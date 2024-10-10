package xenakis.impl

import bundles.PublicProperty
import bundles.publicProperty
import xenakis.model.Logger
import xenakis.model.Logger.Category
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

interface SuperColliderClient : SuperColliderContext {
    val statusListener: StatusListener

    fun sendAsync(address: String, arguments: List<Any> = emptyList())

    fun send(address: String, arguments: List<Any> = emptyList()): Future<String>

    fun eval(code: String): Future<String> {
        if (!canSuperColliderTalkToMe) {
            run(code)
            return CompletableFuture.completedFuture("")
        }
        Logger.fine("eval $code", Category.SuperCollider, detailMessage = code)
        return send("eval", listOf(code))
    }

    override fun run(command: String) {
        if (command == "(\n)\n") return
        Logger.fine("run: $command", Category.SuperCollider)
        try {
            send("eval", listOf(command)).get()
        } catch (e: Exception) {
            System.err.println("Exception while running $command")
            e.printStackTrace()
        }
    }

    override fun run(writeCode: ScWriter.() -> Unit) {
        val command = code(writeCode)
        if (command.isNotBlank()) run(command)
    }

    fun quit()

    companion object : PublicProperty<SuperColliderClient> by publicProperty("SuperColliderClient")

    val consoleMonitor: ConsoleMonitor
}