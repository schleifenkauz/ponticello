package xenakis.sc.client

import bundles.PublicProperty
import bundles.publicProperty
import reaktive.event.EventStream
import xenakis.impl.Logger
import xenakis.impl.Logger.Category
import xenakis.impl.canSuperColliderTalkToMe
import xenakis.impl.code
import xenakis.sc.client.StatusListener.StatusUpdate
import xenakis.ui.launcher.ProgressIndicator
import java.lang.Thread.sleep
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

interface SuperColliderClient : SuperColliderContext {
    val statusListener: StatusListener

    val serverRebooted: EventStream<Unit>
    val treeCleared: EventStream<Unit>

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
            send("eval", listOf(command))
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

    val consoleMonitor: ConsoleMonitor

    fun bootServer(indicator: ProgressIndicator, onReady: () -> Unit) {
        consoleMonitor.addListener(ConsoleMonitor.PipeToSystemOut)
        indicator.displayProgress(0.1, "Starting SuperCollider")
        statusListener.on(StatusUpdate.ScLangBooted) {
            indicator.displayProgress(0.2, "SuperCollider started, connecting via OSC")
            sleep(500)
        }
        statusListener.on(StatusUpdate.OSCReady) {
            indicator.displayProgress(0.3, "OSC connected, booting server")
            sleep(500)
            onReady()
        }
        statusListener.on(StatusUpdate.ServerBooted) {
            indicator.displayProgress(0.9, "Server booted")
            statusListener.remove()
        }
    }

    companion object : PublicProperty<SuperColliderClient> by publicProperty("SuperColliderClient")
}