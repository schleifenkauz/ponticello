package ponticello.model.rubato

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

// UdpLatencyClient.java
object UdpLatencyClient {
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val socket = DatagramSocket()
        socket.soTimeout = 1000 // 1 second timeout

        val serverAddress = InetAddress.getByName("192.168.178.22") // or your server IP
        val serverPort = 9876

        val sendData = "ping".repeat(1000).toByteArray(StandardCharsets.UTF_8)
        val receiveData = ByteArray(sendData.size)

        val tests = 20
        var totalTime: Long = 0

        for (i in 0..<tests) {
            val sendPacket = DatagramPacket(sendData, sendData.size, serverAddress, serverPort)
            val start = System.currentTimeMillis()

            socket.send(sendPacket)

            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            try {
                socket.receive(receivePacket)
                val end = System.currentTimeMillis()
                println(receivePacket.data.decodeToString())
                val rtt = (end - start) // in milliseconds
                totalTime += rtt
                println("RTT $i: $rtt ms")
            } catch (e: SocketTimeoutException) {
                println("RTT $i: timeout")
            }

            Thread.sleep(100) // small pause between tests
        }

        println("Average RTT: " + (totalTime / tests.toDouble()) + " ms")
        socket.close()
    }
}
