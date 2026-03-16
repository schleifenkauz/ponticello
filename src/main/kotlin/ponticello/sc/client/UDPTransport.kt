package ponticello.sc.client

import com.illposed.osc.*
import com.illposed.osc.transport.Transport
import com.illposed.osc.transport.channel.OSCDatagramChannel
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * Adopted from JavaOSC's [com.illposed.osc.transport.udp.UDPTransport].
 * This has the advantage of supporting both sending and receiving data,
 * because it uses two different [ByteBuffer]s instead of just one,
 * which lead to errors when using both [send] and [receive] methods on the same instance.
 */
class UDPTransport @JvmOverloads constructor(
    private val local: SocketAddress?,
    private val remote: SocketAddress,
    serializerAndParserBuilder: OSCSerializerAndParserBuilder? = OSCSerializerAndParserBuilder()
) : Transport {
    private val receiveBuffer: ByteBuffer = ByteBuffer.allocate(BUFFER_SIZE)
    private val sendBuffer: ByteBuffer = ByteBuffer.allocate(BUFFER_SIZE)

    private val channel: DatagramChannel
    private val oscChannel: OSCDatagramChannel

    init {
        val tmpChannel: DatagramChannel
        if ((local is InetSocketAddress) && LibraryInfo.hasStandardProtocolFamily()) {
            val localIsa = local
            val remoteIsa = remote as InetSocketAddress
            val localClass: Class<*> = localIsa.address.javaClass
            val remoteClass: Class<*> = remoteIsa.address.javaClass

            require(localClass == remoteClass) {
                ("local and remote addresses are not of the same family"
                        + " (IP v4 vs v6)")
            }
            if (localIsa.address is Inet4Address) {
                tmpChannel = DatagramChannel.open(StandardProtocolFamily.INET)
            } else if (localIsa.address is Inet6Address) {
                tmpChannel = DatagramChannel.open(StandardProtocolFamily.INET6)
            } else {
                throw IllegalArgumentException(
                    "Unknown address type: "
                            + localIsa.address.javaClass.getCanonicalName()
                )
            }
        } else {
            tmpChannel = DatagramChannel.open()
        }
        this.channel = tmpChannel
        if (LibraryInfo.hasStandardProtocolFamily()) {
            this.channel.setOption<Int?>(StandardSocketOptions.SO_SNDBUF, BUFFER_SIZE)
            // NOTE So far, we never saw an issue with the receive-buffer size,
            //      thus we leave it at its default.
            this.channel.setOption<Boolean?>(StandardSocketOptions.SO_REUSEADDR, true)
            this.channel.setOption<Boolean?>(StandardSocketOptions.SO_BROADCAST, true)
        } else {
            this.channel.socket().sendBufferSize = BUFFER_SIZE
            // NOTE So far, we never saw an issue with the receive-buffer size,
            //      thus we leave it at its default.
            this.channel.socket().reuseAddress = true
            this.channel.socket().broadcast = true
        }
        this.channel.socket().bind(local)
        this.oscChannel = OSCDatagramChannel(channel, serializerAndParserBuilder)
    }

    @Throws(IOException::class)
    override fun connect() {
        checkNotNull(remote) { "Can not connect a socket without a remote address specified" }
        channel.connect(remote)
    }

    @Throws(IOException::class)
    override fun disconnect() {
        channel.disconnect()
    }

    override fun isConnected(): Boolean {
        return channel.isConnected
    }

    /**
     * Close the socket and free-up resources.
     * It is recommended that clients call this when they are done with the port.
     * @throws IOException If an I/O error occurs on the channel
     */
    @Throws(IOException::class)
    override fun close() {
        channel.close()
    }

    @Throws(IOException::class, OSCSerializeException::class)
    override fun send(packet: OSCPacket?) {
        oscChannel.send(sendBuffer, packet, remote)
    }

    @Throws(IOException::class, OSCParseException::class)
    override fun receive(): OSCPacket {
        return oscChannel.read(receiveBuffer)
    }

    override fun isBlocking(): Boolean {
        return channel.isBlocking
    }

    override fun toString(): String {
        return String.format(
            "%s: local=%s, remote=%s", javaClass.getSimpleName(), local, remote
        )
    }

    companion object {
        /**
         * Buffers were 1500 bytes in size, but were increased to 1536, as this
         * is a common MTU, and then increased to 65507, as this is the maximum
         * incoming datagram data size.
         */
        const val BUFFER_SIZE: Int = 65507
    }
}
