package xenakis.impl

import com.illposed.osc.OSCMessage
import xenakis.impl.StatusListener.StatusUpdate
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

class UDPSuperColliderClient private constructor(
    private val sclang: Process,
    private val socket: DatagramSocket,
    private val sclangAdr: InetAddress,
    private val sclangPort: Int
) : SuperColliderContext, SuperColliderClient {
    private val buffer = ByteArray(BUFFER_SIZE)

    override val consoleMonitor = ConsoleMonitor(sclang)

    override val statusListener: StatusListener = StatusListener(consoleMonitor)

    init {
        consoleMonitor.start()
    }

    override fun eval(code: String): CompletableFuture<String> {
        println("shell> $code")
        val msg = createMessage(code)
        send(msg)
        val answer = receiveMessage()
        val value = parseAnswer(answer)
        return CompletableFuture.completedFuture(value)
    }

    override fun run(command: String) {
        println("shell> $command")
        val msg = createMessage(command)
        send(msg)
        thread(isDaemon = true, name = "Command Thread") { receiveMessage() }
    }

    override fun sendAsync(address: String, arguments: List<Any>) {
        TODO("Not yet implemented")
    }

    override fun send(address: String, arguments: List<Any>): CompletableFuture<OSCMessage> {
        TODO("Not yet implemented")
    }

    private fun createMessage(command: String): ByteArray {
        val msg = "$EVAL$STR_TYPE$command".toByteArray()
        val padding = ByteArray(4 - command.length % 4)
        return msg + padding
    }

    private fun parseAnswer(answer: ByteArray): String =
        answer.toString(Charsets.UTF_8).removePrefix("$RESULT$STR_TYPE").dropLastWhile { c -> c == '\u0000' }

    private fun send(msg: ByteArray) {
        val packet = DatagramPacket(msg, msg.size, sclangAdr, sclangPort)
        socket.send(packet)
    }

    private fun receiveMessage(): ByteArray {
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        val answer = packet.data.copyOf(packet.length)
        for (i in packet.data.indices) packet.data[i] = 0
        return answer
    }

    override fun quit() {
        consoleMonitor.interrupt()
        run("s.quit;")
        run("0.exit;")
        socket.disconnect()
        statusListener.status = StatusUpdate.Exited
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val client = create(57120)
            val sc = Scanner(System.`in`)
            while (true) {
                val x = sc.nextLine() ?: break
                val answer = client.eval(x)
                println(answer)
            }
        }

        fun create(port: Int = 57120): UDPSuperColliderClient {
            val sclang = ProcessBuilder(mutableListOf("sclang", "-u", "$port"))
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            sclang.outputStream.write(SETUP_OSC.toByteArray())
            sclang.outputStream.write("\n".toByteArray())
            sclang.outputStream.flush()
            val socket = DatagramSocket()
            val localhost = InetAddress.getLocalHost()
            val client = UDPSuperColliderClient(sclang, socket, localhost, port)
            return client
        }

        private const val BUFFER_SIZE: Int = 1024

        private const val STR_TYPE = ",s\u0000\u0000"
        private const val RESULT = "/result\u0000"
        private const val EVAL = "/eval\u0000\u0000\u0000"

        private const val SETUP_OSC =
            """(o = OSCFunc({ arg msg, time, addr, recvPort; var answer;AppClock.sched(0, {answer = this.interpret(msg[1].asString); addr.sendMsg('/result', answer.asString);nil;});}, '/eval');)"""
    }
}