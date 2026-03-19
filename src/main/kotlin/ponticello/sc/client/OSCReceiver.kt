package ponticello.sc.client

import com.illposed.osc.*
import com.illposed.osc.argument.OSCTimeTag64
import ponticello.impl.Logger
import java.nio.channels.ClosedByInterruptException

class OSCReceiver(private val transport: UDPTransport) : Thread("OSC Receiver") {
    private val listeners = mutableListOf<OSCMessageListener>()
    private val listenersToAddLater = mutableListOf<OSCMessageListener>()
    private var dispatching = false

    init {
        isDaemon = true
    }

    override fun run() {
        while (!interrupted()) {
            try {
                val oscPacket: OSCPacket = transport.receive()
                dispatching = true
                dispatchPacket(oscPacket)
            } catch (ex: ClosedByInterruptException) {
                Logger.info("OSCReceiver stopped", Logger.Category.OSC)
                break
            } catch (ex: Exception) {
                Logger.error("Error while receiving OSC packet", ex)
                break
            } catch (ex: OSCParseException) {
                Logger.error("Error while parsing OSC packet", ex)
            } finally {
                dispatching = false
                listeners.addAll(listenersToAddLater)
            }
        }
        transport.close()
    }

    private fun dispatchPacket(packet: OSCPacket) {
        when (packet) {
            is OSCMessage -> for (listener in listeners) {
                val event = OSCMessageEvent(this, OSCTimeTag64.IMMEDIATE, packet)
                try {
                    listener.acceptMessage(event)
                } catch (e: Exception) {
                    Logger.error("Error while processing OSC message ${packet.address}", e)
                }
            }

            is OSCBundle -> for (packet in packet.packets) {
                dispatchPacket(packet)
            }
        }
    }

    fun addListener(listener: OSCMessageListener) {
        if (dispatching) {
            listenersToAddLater.add(listener)
        } else {
            listeners.add(listener)
        }
    }

    fun addListener(address: String, listener: (time: OSCTimeTag64, msg: OSCMessage) -> Unit) {
        addListener { event ->
            if (event.message.address == address) {
                listener(event.time, event.message)
            }
        }
    }
}
