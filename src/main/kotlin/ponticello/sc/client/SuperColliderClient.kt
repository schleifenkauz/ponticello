package ponticello.sc.client

import bundles.PublicProperty
import bundles.publicProperty
import reaktive.Observer
import java.util.concurrent.CompletableFuture

interface SuperColliderClient : SuperColliderContext, OSCReceiver {
    val sampleRate: Double

    fun onServerBooted(action: () -> Unit): Observer

    fun onTreeCleared(action: () -> Unit)

    fun onClientReady(action: () -> Unit)

    fun send(address: String, arguments: List<Any> = emptyList()): CompletableFuture<String>

    fun sendAsync(address: String, arguments: List<Any> = emptyList())

    fun quit()

    val consoleMonitor: ConsoleMonitor

    companion object : PublicProperty<SuperColliderClient> by publicProperty("SuperColliderClient")
}