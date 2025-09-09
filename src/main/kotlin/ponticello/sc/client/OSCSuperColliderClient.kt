package ponticello.sc.client

import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.messageselector.JavaRegexAddressMessageSelector
import com.illposed.osc.transport.OSCPortIn
import com.illposed.osc.transport.OSCPortOut
import hextant.context.Context
import ponticello.impl.Logger
import ponticello.impl.superColliderPath
import ponticello.model.obj.playbackSettings
import ponticello.ui.launcher.PonticelloFiles
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import reaktive.Observer
import reaktive.event.unitEvent
import reaktive.observe
import reaktive.value.now
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.collections.set

class OSCSuperColliderClient(
    process: Process,
    private val sender: OSCPortOut,
    private val receiver: OSCPortIn,
    override val context: Context
) : SuperColliderClient, OSCMessageListener {
    private var idCounter = 1
    private val waitingForReply = mutableMapOf<Int, PendingRequest>()
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
        addListener(this)
    }

    override fun addListener(listener: OSCMessageListener) {
        receiver.dispatcher.addListener(ALL_MESSAGES, listener)
    }

    override fun sendAsync(address: String, arguments: List<Any>) {
        val adr = if (!address.startsWith('/')) "/$address" else address
        val msg = OSCMessage(adr, listOf(-1) + arguments)
        sender.send(msg)
    }

    override fun send(address: String, arguments: List<Any>, description: String?): CompletableFuture<String> {
        val id = idCounter++
        val future = CompletableFuture<String>()
        val adr = if (!address.startsWith('/')) "/$address" else address
        val msg = OSCMessage(adr, listOf(id) + arguments)
        waitingForReply[id] = PendingRequest(description, msg, future)
        try {
            sender.send(msg)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }
        return future//.orTimeout(10, TimeUnit.SECONDS)
    }

    override fun run(command: String) {
        if (command == "(\n)\n") return
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

    override fun acceptMessage(event: OSCMessageEvent) {
        val address = event.message.address
        when (address) {
            "/ready" -> eventExecutor.execute {
                ready.fire()
            }

            "/booted" -> eventExecutor.execute {
                sampleRate = eval("s.sampleRate").get().toDouble()
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
                    println("Completed ${request.description}")
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
    }

    override fun quit() {
        consoleMonitor.interrupt()
        run("s.quit;")
        run("0.exit;")
        sender.disconnect()
        receiver.close()
        eventExecutor.shutdown()
    }

    private data class PendingRequest(
        val description: String?,
        val oscMessage: OSCMessage,
        val future: CompletableFuture<String>,
    )

    companion object {
        private const val SETUP_FILE = "ponticello_setup.scd"

        private var setupFile: File? = null

        private fun copySetupFile(context: Context): File {
            setupFile?.let { return it }
            val resource = this::class.java.getResourceAsStream(SETUP_FILE) ?: error("Setup file $SETUP_FILE not found")
            val setupScript = resource.bufferedReader().use { r -> r.readText() }
            setupFile = context[PonticelloFiles].resolve(SETUP_FILE)
            setupFile!!.writeText(setupScript)
            return setupFile!!
        }

        fun create(
            context: Context, scPort: Int = OSCPortOut.DEFAULT_SC_LANG_OSC_PORT,
        ): OSCSuperColliderClient {
            val setupFile = copySetupFile(context).superColliderPath
            val sclang = ProcessBuilder(mutableListOf("sclang", "-u", "$scPort"))
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            Thread.sleep(100)
            sclang.outputStream.write("this.executeFile($setupFile);\n".toByteArray())
            sclang.outputStream.flush()
            val localhost = InetAddress.getLoopbackAddress()
            val sender = OSCPortOut(localhost, scPort)
            val receiver = OSCPortIn(7775)
            receiver.startListening()
            return OSCSuperColliderClient(sclang, sender, receiver, context)
        }

        val ALL_MESSAGES = JavaRegexAddressMessageSelector(".*")
    }
}