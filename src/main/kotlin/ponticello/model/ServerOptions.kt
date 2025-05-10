package ponticello.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import ponticello.impl.isWindows
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.BusObject
import ponticello.model.obj.BusReference
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.sc.client.SuperColliderClient

@Serializable
data class ServerOptions(
    var device: String = "",
    var numInputChannels: Int = 2, var numOutputChannels: Int = 2,
    var memSize: Int = 8192, var sampleRate: Int = 44100, var numWireBufs: Int = 8192,
    var recordedBus: BusReference = ObjectReference.none(),
) : AbstractContextualObject() {
    override fun initialize(context: Context) {
        super.initialize(context)
        if (recordedBus == ObjectReference.none<BusObject>()) {
            recordedBus = context[BusRegistry].getOutput().reference()
        }
        recordedBus.resolve(context[BusRegistry])
    }

    fun reboot(client: SuperColliderClient) {
        client.run {
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
        if (initialized) configureIOBuses()
    }

    fun configureIOBuses() {
        val buses = context[BusRegistry]
        buses.get("input").channels.now = numInputChannels
        buses.get("output").channels.now = numOutputChannels
    }

    companion object {
        fun default() = ServerOptions()
    }
}