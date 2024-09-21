package xenakis.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.SuperColliderClient

@Serializable
data class ServerOptions(
    var numInputChannels: Int = 2, var numOutputChannels: Int = 2,
    var memSize: Int = 8192, var sampleRate: Int = 44100
) {
    fun reboot(context: Context) {
        val buses = context[BusRegistry]
        buses.get("input").channels.now = numInputChannels
        buses.get("output").channels.now = numOutputChannels
        context[SuperColliderClient].run {
            +"s.options.numInputBusChannels = $numInputChannels"
            +"s.options.numOutputBusChannels = $numOutputChannels"
            +"s.options.memSize = $memSize"
            +"s.options.sampleRate = $sampleRate"
            +"s.reboot"
        }
    }
}