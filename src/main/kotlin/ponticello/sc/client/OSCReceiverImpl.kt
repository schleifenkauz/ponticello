package ponticello.sc.client

import hextant.core.editor.ListenerManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.nio.ByteBuffer
import java.util.concurrent.Executor

class OSCReceiverImpl(
    private val socket: DatagramSocket,
    private val executor: Executor
) : OSCReceiver, Thread() {
    private val listeners = ListenerManager.createWeakListenerManager<OSCListener>()

    override fun addListener(listener: OSCListener) {
        listeners.addListener(listener)
    }

    override fun removeListener(listener: OSCListener) {
        listeners.removeListener(listener)
    }

    override fun run() {
        while (!interrupted() && !socket.isClosed) {
            //IMPORTANT: this loop may not call any blocking methods
            //Otherwise communication with SuperCollider will stop working
            val buf = ByteArray(4096)
            val packet = DatagramPacket(buf, buf.size)
            try {
                socket.receive(packet)
            } catch (e: SocketException) {
                if (e.message == "Socket closed") {
                    println("Closed receiver socket")
                    break
                } else {
                    e.printStackTrace()
                    continue
                }
            }
            val (path, id, content) = parseMessage(buf)
            executor.execute {
                listeners.notifyListeners { onMessage(path, id, content) }
            }
            try {
                sleep(10)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    override fun close() {
        socket.close()
    }

    companion object {
        private fun parseMessage(buf: ByteArray): Triple<String, Int, String> {
            var idx = 0
            while (idx < buf.size && buf[idx].toInt() != 0) idx++
            val path = String(buf, 0, idx)
            while (idx % 4 != 0) idx++
            val id = ByteBuffer.wrap(buf, idx, 4).getInt()
            idx += 4
            var len = 0
            while (len + idx < buf.size && buf[len + idx].toInt() != 0) len++
            val content = String(buf, idx, len)
            return Triple(path, id, content)
        }
    }
}