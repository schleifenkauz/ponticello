package ponticello.sc.client

import bundles.PublicProperty
import bundles.publicProperty
import ponticello.impl.Logger
import ponticello.impl.Logger.Category
import ponticello.impl.canSuperColliderTalkToMe
import ponticello.impl.writeCode
import reaktive.Observer
import java.util.concurrent.CompletableFuture

interface SuperColliderClient : SuperColliderContext {
    val sampleRate: Double

    fun onServerBooted(action: () -> Unit): Observer

    fun onTreeCleared(action: () -> Unit)

    fun onClientReady(action: () -> Unit)

    fun addListener(listener: OSCListener)

    fun removeListener(listener: OSCListener)

    fun sendAsync(address: String, arguments: List<Any> = emptyList())

    fun send(address: String, arguments: List<Any> = emptyList()): CompletableFuture<String>

    fun eval(code: String): CompletableFuture<String> {
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
            sendAsync("run", listOf(command))
        } catch (e: Exception) {
            System.err.println("Exception while running $command")
            e.printStackTrace()
        }
    }

    override fun run(writeCode: ScWriter.() -> Unit) {
        val command = writeCode(writeCode)
        if (command.isNotBlank()) run(command)
    }

    fun quit()

    val consoleMonitor: ConsoleMonitor

    companion object : PublicProperty<SuperColliderClient> by publicProperty("SuperColliderClient")
}