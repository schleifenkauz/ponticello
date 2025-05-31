package ponticello.sc.client

import com.illposed.osc.OSCMessage
import com.illposed.osc.transport.OSCPortOut
import com.illposed.osc.transport.OSCPortOutBuilder
import ponticello.impl.Logger
import ponticello.impl.superColliderPath
import reaktive.Observer
import reaktive.event.unitEvent
import reaktive.observe
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.toPath

class OSCSuperColliderClient(
    process: Process,
    private val sender: OSCPortOut,
    private val receiver: OSCReceiver,
) : SuperColliderClient, Thread(), OSCReceiver by receiver, OSCListener {
    private var idCounter = 0
    private val waitingForReply = mutableMapOf<Int, CompletableFuture<String>>()
    private val eventExecutor = Executors.newSingleThreadExecutor()

    override val consoleMonitor: ConsoleMonitor = ConsoleMonitor(process)

    private val eventObservers = mutableListOf<Observer>()
    private val ready = unitEvent()
    private val serverBoot = unitEvent()
    private val treeClear = unitEvent()

    override fun onServerBooted(action: () -> Unit): Observer {
        val observer = serverBoot.stream.observe(action)
        eventObservers.add(observer)
        return observer
    }

    override fun onTreeCleared(action: () -> Unit) {
        eventObservers.add(treeClear.stream.observe(action))
    }

    override fun onClientReady(action: () -> Unit) {
        eventObservers.add(ready.stream.observe(action))
    }

    override var sampleRate: Double = -1.0
        private set

    init {
        consoleMonitor.start()
        isDaemon = true
        start()
    }

    override fun sendAsync(address: String, arguments: List<Any>) {
        val adr = if (!address.startsWith('/')) "/$address" else address
        val msg = OSCMessage(adr, listOf(-1) + arguments)
        sender.send(msg)
    }

    override fun send(address: String, arguments: List<Any>): CompletableFuture<String> {
        val id = idCounter++
        val future = CompletableFuture<String>()
        val adr = if (!address.startsWith('/')) "/$address" else address
        val msg = OSCMessage(adr, listOf(id) + arguments)
        waitingForReply[id] = future
        try {
            sender.send(msg)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }
        return future.orTimeout(3, TimeUnit.SECONDS)
    }

    override fun run(command: String) {
        if (command == "(\n)\n") return
        Logger.fine("run: $command", Logger.Category.SuperCollider)
        try {
            sendAsync("run", listOf(command))
        } catch (e: Exception) {
            System.err.println("Exception while running $command")
            e.printStackTrace()
        }
    }

    override fun onMessage(path: String, id: Int, content: String) {
        when {
            path.startsWith("/ready") -> eventExecutor.execute {
                ready.fire()
            }

            path.startsWith("/booted") -> eventExecutor.execute {
                sampleRate = eval("s.sampleRate").get().toDouble()
                serverBoot.fire()
            }

            path.startsWith("/cleared") -> eventExecutor.execute {
                treeClear.fire()
            }

            path.startsWith("/reply") -> {
                Logger.fine("Completed id: $id, result: $content", Logger.Category.SuperCollider)
                val future = waitingForReply.remove(id)
                if (future == null) {
                    Logger.error("Wasn't waiting for a reply for id $id")
                    return
                }
                future.complete(content)
            }

            path.startsWith("/error") -> {
                Logger.warn(content, Logger.Category.SuperCollider)
                if (id != -1) {
                    val future = waitingForReply.remove(id)
                    if (future == null) {
                        Logger.error("Wasn't waiting for a reply for id $id")
                        return
                    }
                    future.completeExceptionally(SuperColliderException(content))
                }
            }
        }

    }

    override fun quit() {
        consoleMonitor.interrupt()
        run("s.quit;")
        run("0.exit;")
        interrupt()
        sender.disconnect()
        receiver.close()
        eventExecutor.shutdown()
    }

    companion object {
        private const val SETUP_FILE = "ponticello_setup.scd"

        fun create(scPort: Int = OSCPortOut.DEFAULT_SC_LANG_OSC_PORT): OSCSuperColliderClient {
            val setupFile = this::class.java.getResource(SETUP_FILE)!!.toURI().toPath().toFile().superColliderPath
            val sclang = ProcessBuilder(mutableListOf("sclang", "-u", "$scPort"))
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            sleep(100)
            sclang.outputStream.write("this.executeFile($setupFile);\n".toByteArray())
            sclang.outputStream.flush()
            val localhost = InetAddress.getLoopbackAddress()
            val myPort = 7774
            val local = InetSocketAddress(localhost, myPort)
            val remote = InetSocketAddress(localhost, scPort)
            println("local: $local, remote: $remote")
            val sender = OSCPortOutBuilder()
                .setLocalSocketAddress(local)
                .setRemoteSocketAddress(remote)
                .build()
            val socket = DatagramSocket(myPort + 1)
            val receiver = OSCReceiver.create(socket)
            return OSCSuperColliderClient(sclang, sender, receiver)
        }
    }
}