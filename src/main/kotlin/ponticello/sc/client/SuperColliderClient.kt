package ponticello.sc.client

import bundles.PublicProperty
import bundles.publicProperty
import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.argument.OSCTimeTag64
import hextant.context.Context
import reaktive.Observer
import java.util.concurrent.CompletableFuture

interface SuperColliderClient : SuperColliderContext {
    val context: Context

    val sampleRate: Double

    fun onServerBooted(action: () -> Unit): Observer

    fun onTreeCleared(action: () -> Unit)

    fun onClientReady(action: () -> Unit)

    fun send(address: String, arguments: List<Any> = emptyList(), description: String? = null): CompletableFuture<String>

    fun sendAsync(address: String, arguments: List<Any> = emptyList())

    fun addListener(listener: OSCMessageListener)

    fun addListener(address: String, listener: (time: OSCTimeTag64, msg: OSCMessage) -> Unit)

    fun quit()

    val consoleMonitor: ConsoleMonitor

    companion object : PublicProperty<SuperColliderClient> by publicProperty("SuperColliderClient")
}