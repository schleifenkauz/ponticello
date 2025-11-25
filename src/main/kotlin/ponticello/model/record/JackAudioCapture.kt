package ponticello.model.record

import org.jaudiolibs.jnajack.*
import ponticello.impl.Logger
import java.util.*

class JackAudioCapture(private val sourceDevice: String) : AbstractAudioCapture() {
    private var ports: List<JackPort> = emptyList()
    private var running = false

    override fun doPrepare(): Boolean {
        val sourcePorts = getOutputPorts(sourceDevice)
        if (sourcePorts.size != channelConfig.inputChannels) {
            Logger.error("Invalid number of input ports: ${sourcePorts.size}. Expected ${channelConfig.inputChannels}.")
            return false
        }
        ports = List(channelConfig.outputChannels) { idx ->
            val portName = "in_$sourceDevice$idx"
            try {
                client.registerPort(portName, JackPortType.AUDIO, JackPortFlags.JackPortIsInput)
            } catch (e: JackException) {
                Logger.error("Error registering port $portName", e)
                return false
            }
        }
        for ((idx, port) in ports.withIndex()) {
            try {
                jack.connect(client, sourcePorts[idx], port.name)
            } catch (e: JackException) {
                Logger.error("Error connecting port ${port.name} to source port ${sourcePorts[idx]}", e)
                return false
            }
        }
        instances.add(this)
        return true
    }

    override fun doStart(): Boolean {
        if (ports.isEmpty()) {
            Logger.warn("JackAudioCapture is not prepared.", Logger.Category.Recording)
            return false
        }
        running = true
        return true
    }

    override fun doStop() {
        running = false
    }

    override fun doClose() {
        instances.remove(this)
        for (port in ports) {
            try {
                client.unregisterPort(port)
            } catch (e: JackException) {
                Logger.error("Error unregistering port ${port.shortName}", e)
            }
        }
    }

    private fun process(frames: Int) {
        if (!running) return
        buffer.receive(ports.map { p -> p.floatBuffer }, frames)
    }

    companion object {
        private val jack = Jack.getInstance()

        private val instances = mutableListOf<JackAudioCapture>()

        private val client by lazy { setupClient() }

        private fun setupClient(): JackClient {
            val options = EnumSet.noneOf(JackOptions::class.java)
            val status = EnumSet.noneOf(JackStatus::class.java)
            val client = jack.openClient("ponticello", options, status)
            client.setProcessCallback { _, nframes ->
                for (inst in instances) {
                    inst.process(nframes)
                }
                true
            }
            client.activate()
            return client
        }

        fun getSources(): Set<String> =
            jack.getPorts(client, ".*", JackPortType.AUDIO, EnumSet.of(JackPortFlags.JackPortIsOutput))
                .mapTo(mutableSetOf()) { portName ->
                    val (clientName, _) = portName.split(":")
                    clientName
                }

        private fun getOutputPorts(source: String): List<String> = jack.getPorts(
            client, "$source:.*",
            JackPortType.AUDIO, EnumSet.of(JackPortFlags.JackPortIsOutput)
        ).asList()

        fun getOutputChannels(source: String): Int = getOutputPorts(source).size
    }
}