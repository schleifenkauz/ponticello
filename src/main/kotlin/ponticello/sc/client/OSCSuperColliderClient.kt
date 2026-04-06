package ponticello.sc.client

import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.argument.OSCTimeTag64
import com.illposed.osc.messageselector.JavaRegexAddressMessageSelector
import hextant.context.Context
import ponticello.impl.Logger
import ponticello.model.obj.playbackSettings
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import reaktive.Observer
import reaktive.event.unitEvent
import reaktive.observe
import reaktive.value.now
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class OSCSuperColliderClient(
    process: Process,
    private val clientTransport: UDPTransport,
    override val context: Context
) : SuperColliderClient, OSCMessageListener {
    private var isReady = false
    private var idCounter = 1
    private val waitingForReply = mutableMapOf<Int, PendingRequest>()
    private val eventExecutor = Executors.newSingleThreadExecutor()

    override val consoleMonitor: ConsoleMonitor = ConsoleMonitor(process.inputStream)

    private val clientReceiver = OSCReceiver(clientTransport).also(Thread::start)
    private lateinit var serverTransport: UDPTransport
    override lateinit var serverReceiver: OSCReceiver
        private set

    private val eventObservers = mutableListOf<Observer>()
    private val ready = unitEvent()
    private val serverBoot = unitEvent()
    private val treeClear = unitEvent()

    override fun onServerBooted(action: () -> Unit): Observer {
        val observer = serverBoot.stream.observe(action)
        eventObservers.add(observer)
        if (isReady && eval("s.hasBooted").get() == "true") {
            action()
        }
        return observer
    }

    override fun onTreeCleared(initially: Boolean, action: () -> Unit) {
        eventObservers.add(treeClear.stream.observe(action))
        if (isReady && initially && eval("s.hasBooted").get() == "true") {
            action()
        }
    }

    override fun onClientReady(action: () -> Unit) {
        eventObservers.add(ready.stream.observe(action))
        if (isReady) {
            action()
        }
    }

    override var sampleRate: Double = -1.0
        private set

    init {
        consoleMonitor.start()
        clientReceiver.addListener(this)
    }

    override fun addListener(listener: OSCMessageListener) {
        clientReceiver.addListener(listener)
    }

    override fun addListener(
        address: String, vararg moreAddresses: String,
        listener: (time: OSCTimeTag64, msg: OSCMessage) -> Unit
    ) {
        addListener { event ->
            if (event.message.address == address || event.message.address in moreAddresses) {
                listener(event.time, event.message)
            }
        }
    }

    override fun sendAsync(address: String, arguments: List<Any?>) {
        val adr = if (!address.startsWith('/')) "/$address" else address
        val msg = OSCMessage(adr, arguments)
        synchronized(clientTransport) {
            clientTransport.send(msg)
        }
    }

    override fun send(address: String, arguments: List<Any>, description: String?): CompletableFuture<String> {
        val id = idCounter++
        val future = CompletableFuture<String>()
        val adr = if (!address.startsWith('/')) "/$address" else address
        val msg = OSCMessage(adr, listOf(id) + arguments)
        waitingForReply[id] = PendingRequest(description, msg, future)
        try {
            synchronized(clientTransport) {
                clientTransport.send(msg)
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }
        return future//.orTimeout(10, TimeUnit.SECONDS)
    }

    override fun run(command: String) {
        if (command.all { c -> c.isWhitespace() || c in DELIMITERS }) return
        Logger.fine("run: $command", Logger.Category.SuperCollider)
        if (!context.hasProperty(currentProject) || context.playbackSettings.logScCode.now) {
            println("################ RUN #################")
            println(command)
            println("################ END #################")
        }
        try {
            sendAsync("run", listOf(command))
        } catch (e: Exception) {
            System.err.println("Exception while running $command")
            e.printStackTrace()
        }
    }

    private fun addPonticelloToClientList() {
        val serverPort = eval("s.addr.port").get().toInt()
        val local = InetSocketAddress(PONTICELLO_PORT + 1)
        val remote = InetSocketAddress(InetAddress.getLoopbackAddress(), serverPort)
        serverTransport = UDPTransport(local, remote)
        serverReceiver = OSCReceiver(serverTransport).also(Thread::start)
        serverTransport.send(OSCMessage("/notify", listOf(1)))
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        try {
            val address = event.message.address
            when (address) {
                "/ready" -> eventExecutor.execute {
                    ready.fire()
                    isReady = true
                }

                "/booted" -> eventExecutor.execute {
                    sampleRate = eval("s.sampleRate").get().toDouble()
                    addPonticelloToClientList()
                    serverBoot.fire()
                }

                "/cleared" -> eventExecutor.execute {
                    treeClear.fire()
                }

                "/reply" -> {
                    val id = event.message.id
                    val result = event.message.getArgument<String>(1, "result")
                    Logger.fine("Completed id: $id, result: $result", Logger.Category.SuperCollider)
                    val request = waitingForReply.remove(id)
                    if (request == null) {
                        Logger.warn("Wasn't waiting for a reply for id $id", Logger.Category.SuperCollider)
                        return
                    }
                    if (request.description != null) {
                        println("Completed ${request.description}: $result")
                    }
                    request.future.complete(result)
                }

                "/error" -> {
                    val message = event.message.getArgument<String>(1, "message") ?: "<unknown>"
                    val id = event.message.id
                    Logger.warn(message, Logger.Category.SuperCollider)
                    if (id != null && id != -1) {
                        val request = waitingForReply.remove(id)
                        if (request == null) {
                            Logger.error("Wasn't waiting for a reply for id $id")
                            return
                        }
                        val exception = SuperColliderException(request.description, request.oscMessage, message)
                        request.future.completeExceptionally(exception)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("Error while processing OSC message", e)
        }
    }

    override fun quit() {
        consoleMonitor.interrupt()
        run("s.quit;")
        run("0.exit;")
        clientReceiver.interrupt()
        serverReceiver.interrupt()
        eventExecutor.shutdown()
    }

    private data class PendingRequest(
        val description: String?,
        val oscMessage: OSCMessage,
        val future: CompletableFuture<String>,
    )


    companion object {
        val ALL_MESSAGES = JavaRegexAddressMessageSelector(".*")

        val DELIMITERS = "()[]{};:,".toSet()

        private const val PONTICELLO_PORT = 7775

        fun create(context: Context, scPort: Int): OSCSuperColliderClient {
            val sclang = ProcessBuilder(
                "sclang", "-u", "$scPort", "-i", "ponticello"
            ).redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            val local = InetSocketAddress(PONTICELLO_PORT)
            val remote = InetSocketAddress(InetAddress.getLoopbackAddress(), scPort)
            val transport = UDPTransport(local, remote)
            return OSCSuperColliderClient(sclang, transport, context)
        }
    }
}