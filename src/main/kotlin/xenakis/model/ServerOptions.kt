package xenakis.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.isWindows
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectReference
import xenakis.sc.client.SuperColliderClient

@Serializable
data class ServerOptions(
    var device: String = "",
    var numInputChannels: Int = 2, var numOutputChannels: Int = 2,
    var memSize: Int = 8192, var sampleRate: Int = 44100, var numWireBufs: Int = 8192,
    var recordedBus: ObjectReference? = null
) : XenakisProject.ProjectComponent {
    override val componentName: String
        get() = "server_options"

    fun initialize(context: Context) {
        if (recordedBus != null) recordedBus!!.resolve(context[BusRegistry])
    }

    fun reboot(context: Context) {
        val buses = context[BusRegistry]
        buses.get("input").channels.now = numInputChannels
        buses.get("output").channels.now = numOutputChannels
        context[SuperColliderClient].run {
            if (isWindows) {
                +"s.options.device_(${if (device.isEmpty()) "nil" else "\"$device\""})"
            }
            +"s.options.numInputBusChannels = $numInputChannels"
            +"s.options.numOutputBusChannels = $numOutputChannels"
            +"s.options.memSize = $memSize"
            +"s.options.numWireBufs = $numWireBufs"
            +"s.options.sampleRate = $sampleRate"
            +"s.reboot"
        }
    }
}