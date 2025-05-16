package ponticello.model.rubato

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

// UdpEchoServer.java
object UdpEchoServer {
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val socket = DatagramSocket(9876, InetAddress.getByName("192.168.178.22"))
        val receiveData = ByteArray(4000)
        println("UDP Echo Server started on port 9876...")

        while (true) {
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            socket.receive(receivePacket)

            val clientAddress = receivePacket.address
            val clientPort = receivePacket.port
            val sendData = receivePacket.data

            val sendPacket = DatagramPacket(sendData, sendData.size, clientAddress, clientPort)
            socket.send(sendPacket)
        }
    }
}
