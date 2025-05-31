package ponticello.sc.client

import java.net.DatagramSocket
import java.util.concurrent.Executors

interface OSCReceiver {
    fun addListener(listener: OSCListener)

    fun removeListener(listener: OSCListener)

    fun close()

    companion object {
        fun create(socket: DatagramSocket): OSCReceiver {
            val executor = Executors.newSingleThreadExecutor()
            return OSCReceiverImpl(socket, executor)
        }

        fun create(port: Int): OSCReceiver = create(DatagramSocket(port))
    }
}