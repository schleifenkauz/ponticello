package xenakis.sc.client

import com.illposed.osc.OSCMessage
import com.illposed.osc.transport.OSCPortOut
import com.illposed.osc.transport.OSCPortOutBuilder
import reaktive.Observer
import reaktive.event.unitEvent
import reaktive.observe
import xenakis.impl.Logger
import xenakis.impl.superColliderPath
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.io.path.toPath

class OSCSuperColliderClient(
    process: Process,
    private val sender: OSCPortOut,
    private val receiver: DatagramSocket,
) : SuperColliderClient, Thread() {
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
        val msg = OSCMessage(adr, arguments)
        sender.send(msg)
    }

    override fun send(address: String, arguments: List<Any>): Future<String> {
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

    override fun run() {
        while (!interrupted() && !receiver.isClosed) {
            //IMPORTANT: this loop may not call any blocking methods
            //Otherwise communication with SuperCollider will stop working
            val buf = ByteArray(4096)
            val packet = DatagramPacket(buf, buf.size)
            try {
                receiver.receive(packet)
            } catch (e: SocketException) {
                if (e.message == "Socket closed") {
                    println("Closed receiver socket")
                    break
                } else {
                    e.printStackTrace()
                    continue
                }
            }
            val path = String(buf, 0, 8)
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
                    val result = getContentString(buf)
                    val id = getId(buf)
                    Logger.fine("Completed id: $id, result: $result", Logger.Category.SuperCollider)
                    val future = waitingForReply.remove(id)
                    if (future == null) {
                        Logger.error("Wasn't waiting for a reply for id $id")
                        continue
                    }
                    future.complete(result)
                }

                path.startsWith("/error") -> {
                    val message = getContentString(buf)
                    val id = getId(buf)
                    Logger.warn(message, Logger.Category.SuperCollider)
                    if (id != -1) {
                        val future = waitingForReply.remove(id)
                        if (future == null) {
                            Logger.error("Wasn't waiting for a reply for id $id")
                            continue
                        }
                        future.completeExceptionally(SuperColliderException(message))
                    }
                }
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

    private fun getId(buf: ByteArray) = ByteBuffer.wrap(buf, 12, 4).getInt()

    private fun getContentString(buf: ByteArray): String {
        var len = 0
        while (len + 16 < buf.size && buf[len + 16].toInt() != 0) len++
        val result = String(buf, 16, len)
        return result
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
    }
}