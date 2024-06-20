package xenakis.impl

import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.messageselector.OSCPatternAddressMessageSelector
import com.illposed.osc.transport.OSCPortIn
import com.illposed.osc.transport.OSCPortInBuilder
import com.illposed.osc.transport.OSCPortOut
import com.illposed.osc.transport.OSCPortOutBuilder
import xenakis.impl.StatusListener.StatusUpdate
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import kotlin.io.path.toPath

class OSCSuperColliderClient(
    process: Process,
    private val sender: OSCPortOut,
    private val receiver: OSCPortIn
) : OSCMessageListener, SuperColliderClient {
    private var idCounter = 0
    private val waitingForReply = mutableMapOf<Int, CompletableFuture<OSCMessage>>()

    override val consoleMonitor: ConsoleMonitor = ConsoleMonitor(process)
    override val statusListener: StatusListener = StatusListener(consoleMonitor)

    init {
        receiver.dispatcher.addListener(OSCPatternAddressMessageSelector("/reply"), this)
        receiver.connect()
        receiver.startListening()
        println(receiver.isListening)
        consoleMonitor.start()
    }

    override fun sendAsync(address: String, arguments: List<Any>) {
        val adr = if (!address.startsWith('/')) "/$address" else address
        val msg = OSCMessage(adr, arguments)
        sender.send(msg)
    }

    override fun send(address: String, arguments: List<Any>): CompletableFuture<OSCMessage> {
        val id = idCounter++
        val future = CompletableFuture<OSCMessage>()
        val adr = if (!address.startsWith('/')) "/$address" else address
        val msg = OSCMessage(adr, listOf(id) + arguments)
        waitingForReply[id] = future
        sender.send(msg)
        return future
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        val msg = event.message
        val id = msg.arguments[0] as? Int ?: error("Reply message didn't have an id!")
        println("Received reply: $id")
        val future = waitingForReply.remove(id) ?: error("Wasn't waiting for a reply for id $id")
        future.complete(msg)
    }

    override fun quit() {
        consoleMonitor.interrupt()
        run("s.quit;")
        run("0.exit;")
        sender.disconnect()
        receiver.stopListening()
        receiver.disconnect()
        statusListener.status = StatusUpdate.Exited
    }

    companion object {
        private const val SETUP_FILE = "xenakis_setup.scd"

        fun create(port: Int = OSCPortOut.DEFAULT_SC_LANG_OSC_PORT): OSCSuperColliderClient {
            val setupFile = this::class.java.getResource(SETUP_FILE)!!.toURI().toPath().toFile().superColliderPath
            val sclang = ProcessBuilder(mutableListOf("sclang", "-u", "$port"))
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            Thread.sleep(100)
            sclang.outputStream.write("this.executeFile($setupFile);\n".toByteArray())
            sclang.outputStream.flush()
            val localhost = InetAddress.getLoopbackAddress()
            val local = InetSocketAddress(localhost, 51730)
            val remote = InetSocketAddress(localhost, port)
            println(remote)
            val sender = OSCPortOutBuilder()
                .setLocalSocketAddress(local)
                .setRemoteSocketAddress(remote)
                .build()
            val receiver = OSCPortInBuilder()
                .setSocketAddress(local)
                .build()
            return OSCSuperColliderClient(sclang, sender, receiver)
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val client = create()
            Thread.sleep(2000)
            client.run("'hello'.postln")
        }
    }
}