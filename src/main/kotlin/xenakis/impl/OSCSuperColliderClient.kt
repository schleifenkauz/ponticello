package xenakis.impl

import com.illposed.osc.OSCMessage
import com.illposed.osc.transport.OSCPortOut
import com.illposed.osc.transport.OSCPortOutBuilder
import xenakis.impl.StatusListener.StatusUpdate
import xenakis.model.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import kotlin.io.path.toPath

class OSCSuperColliderClient(
    process: Process,
    private val sender: OSCPortOut,
    private val receiver: DatagramSocket
) : SuperColliderClient, Thread() {
    private var idCounter = 0
    private val waitingForReply = mutableMapOf<Int, CompletableFuture<String>>()

    override val consoleMonitor: ConsoleMonitor = ConsoleMonitor(process)
    override val statusListener: StatusListener = StatusListener(consoleMonitor)

    init {
        consoleMonitor.start()
        isDaemon = true
        start()
    }

    override fun sendAsync(address: String, arguments: List<Any>) {
        val adr = if (!address.startsWith('/')) "/$address" else address
        val msg = OSCMessage(adr, arguments)
        sender.send(msg)
    }

    override fun send(address: String, arguments: List<Any>): CompletableFuture<String> {
        val id = idCounter++
        val future = CompletableFuture<String>()
        val adr = if (!address.startsWith('/')) "/$address" else address
        val msg = OSCMessage(adr, listOf(id) + arguments)
        waitingForReply[id] = future
        sender.send(msg)
        return future
    }

    override fun run() {
        while (!interrupted()) {
            val buf = ByteArray(4096)
            val packet = DatagramPacket(buf, buf.size)
            receiver.receive(packet)
            if (String(buf, 0, 8).startsWith("/reply")) {
                var len = 0
                while (len + 16 < buf.size && buf[len + 16].toInt() != 0) len++
                val result = String(buf, 16, len)
                val id = ByteBuffer.wrap(buf, 12, 4).getInt()
                Logger.fine("id: $id, result: $result", Logger.Category.SuperCollider)
                val future = waitingForReply.remove(id)
                if (future == null) {
                    Logger.error("Wasn't waiting for a reply for id $id")
                    continue
                }
                future.complete(result)
            }
            try {
                sleep(10)
            } catch (e: InterruptedException) {
                receiver.close()
                return
            }
        }
        receiver.close()
    }

    override fun quit() {
        consoleMonitor.interrupt()
        run("s.quit;")
        run("0.exit;")
        sender.disconnect()
        interrupt()
        statusListener.status = StatusUpdate.Exited
    }

    companion object {
        private const val SETUP_FILE = "xenakis_setup.scd"

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
            val myPort = 7771
            val local = InetSocketAddress(localhost, myPort)
            val remote = InetSocketAddress(localhost, scPort)
            println("local: $local, remote: $remote")
            val sender = OSCPortOutBuilder()
                .setLocalSocketAddress(local)
                .setRemoteSocketAddress(remote)
                .build()
            val receiver = DatagramSocket(myPort + 1)
            return OSCSuperColliderClient(sclang, sender, receiver)
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val client = create()
            sleep(200000)
            client.run("'hello'.postln")
        }
    }
}