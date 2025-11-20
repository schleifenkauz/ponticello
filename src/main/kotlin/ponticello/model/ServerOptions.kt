package ponticello.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.impl.isWindows
import ponticello.model.instr.BusObject
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.BusReference
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.model.server.BusRegistry
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import reaktive.value.now

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

    fun configureOptions(client: SuperColliderClient) {
        client.eval {
            if (isWindows) {
                +"s.options.device_(${if (device.isEmpty()) "nil" else "\"$device\""})"
            }
            +"s.options.numInputBusChannels = $numInputChannels"
            +"s.options.numOutputBusChannels = $numOutputChannels"
            +"s.options.memSize = $memSize"
            +"s.options.numWireBufs = $numWireBufs"
            +"s.options.sampleRate = $sampleRate"
        }.join()
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