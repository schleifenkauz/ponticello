package ponticello.model.record

import org.jaudiolibs.jnajack.*
import java.util.*

class JackAudioCapture {
    companion object {
        fun create(channels: Int) {
            val options = EnumSet.noneOf(JackOptions::class.java)
            val status = EnumSet.noneOf(JackStatus::class.java)
            val client = Jack.getInstance().openClient("ponticello", options, status)
            client.activate()
            val ports = mutableListOf<JackPort>()
            for (ch in 0 until channels) {
                val port = client.registerPort("", JackPortType.AUDIO, JackPortFlags.JackPortIsInput)
                ports.add(port)
            }
            client.setProcessCallback { x, frames ->
                for (port in ports) {
                    port.floatBuffer
                }
                true
            }
        }
    }
}