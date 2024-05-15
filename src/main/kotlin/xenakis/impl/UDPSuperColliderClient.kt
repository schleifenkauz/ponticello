package xenakis.impl

import bundles.PublicProperty
import bundles.publicProperty
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*
import kotlin.concurrent.thread

class UDPSuperColliderClient private constructor(
    private val sclang: Process,
    private val socket: DatagramSocket,
    private val sclangAdr: InetAddress,
    private val sclangPort: Int
) : SuperColliderContext {
    private val buffer = ByteArray(BUFFER_SIZE)
    private var consoleMonitors = mutableListOf<(String) -> Unit>()
    var status: Status = Status.Starting
        private set(value) {
            field = value
            statusListeners.forEach { handler -> handler.invoke(value) }
        }
    private var statusListeners = mutableListOf<(Status) -> Unit>()
    private val consoleUntilNow = StringBuilder()
    private var consoleMonitorThread: Thread

    init {
        consoleMonitorThread = monitorConsole()
    }

    fun post(command: String): String {
        println("shell> $command")
        val msg = createMessage(command)
        send(msg)
        val answer = receiveMessage()
        val value = parseAnswer(answer)
        return value
    }

    override fun postAsync(command: String) {
        println("shell> $command")
        val msg = createMessage(command)
        send(msg)
        thread(isDaemon = true, name = "Command Thread") { receiveMessage() }
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

    private fun monitorConsole(): Thread {
        val stream = sclang.inputStream.reader(Charsets.UTF_8)
        return thread(isDaemon = true, name = "SuperCollider Console Monitor") {
            stream.useLines { lines ->
                for (line in lines) {
                    if (Thread.interrupted()) break
                    consoleMonitors.forEach { m -> m.invoke(line + "\n") }
                    consoleUntilNow.appendLine(line)
                    println(line)
                    if ("SuperCollider 3 server ready." in line) {
                        status = Status.Listening
                    }
                }
            }
        }
    }

    fun addConsoleMonitor(handler: (String) -> Unit) {
        handler.invoke(consoleUntilNow.toString())
        consoleMonitors.add(handler)
    }

    fun removeConsoleMonitor(handler: (String) -> Unit) {
        consoleMonitors.remove(handler)
    }

    fun addStatusListener(handler: (Status) -> Unit) {
        statusListeners.add(handler)
    }

    fun waitForExit() {
        sclang.waitFor()
    }

    fun quit() {
        consoleMonitorThread.interrupt()
        postAsync("s.quit;")
        postAsync("0.exit;")
        socket.disconnect()
        status = Status.Quit
    }

    enum class Status { Starting, Listening, Quit }

    companion object : PublicProperty<UDPSuperColliderClient> by publicProperty("SuperColliderClient") {
        @JvmStatic
        fun main(args: Array<String>) {
            val client = create(57120)
            val sc = Scanner(System.`in`)
            while (true) {
                val x = sc.nextLine() ?: break
                val answer = client.post(x)
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
            sclang.outputStream.write("s.boot;\n".toByteArray())
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