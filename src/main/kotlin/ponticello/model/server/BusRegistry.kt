package ponticello.model.server

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.OSCMessage
import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.model.instr.BusObject
import ponticello.model.obj.SuperColliderObject
import ponticello.model.registry.SuperColliderObjectRegistry
import ponticello.sc.Rate
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument
import ponticello.sc.client.run
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class BusRegistry(
    override val objects: MutableList<BusObject>,
) : SuperColliderObjectRegistry<BusObject>() {
    override val objectType: String
        get() = "Bus"

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.ServerBoot

    @Transient
    private val channelsByReplyId = mutableMapOf<Int, BusChannel>()

    @Transient
    private val channelsByBus = mutableMapOf<BusObject.AudioBus, List<BusChannel>>()

    override fun initialize(context: Context) {
        context[BusRegistry] = this
        super.initialize(context)
        client.onTreeCleared {
            client.run {
                for (bus in all().filterIsInstance<BusObject.ControlBus>()) {
                    bus.run { setDefaultValue(skipIfZero = false) }
                }
            }
        }
        context[SuperColliderClient].addListener("/bus_level") { _, msg -> receivedBusLevel(msg) }
    }

    private fun receivedBusLevel(msg: OSCMessage) {
        val replyId = msg.getArgument<Int>(0, "replyID") ?: return
        val level = msg.getArgument<Float>(1, "level") ?: return
        val channel = channelsByReplyId[replyId]
        if (channel == null) {
            Logger.warn("Received bus_level message: Channel '$replyId' not found.", Logger.Category.SuperCollider)
            return
        }
        channel.level.set(level.toDouble())
    }

    fun getLevels(bus: BusObject.AudioBus): List<ReactiveValue<Double>> =
        channelsByBus[bus]?.map { ch -> ch.level } ?: emptyList()

    fun getLevel(bus: BusObject.AudioBus, channel: Int): ReactiveVariable<Double>? {
        val channels = channelsByBus[bus] ?: return null
        return channels[channel].level
    }

    fun registerLevelSends(bus: BusObject.AudioBus): List<Int> { //TODO reuse existing BusChannels
        val channels = mutableListOf<BusChannel>()
        val replyIds = mutableListOf<Int>()
        for (channel in 0 until bus.channels.now) {
            val replyId = replyIdCounter++
            val level = reactiveVariable(Double.NEGATIVE_INFINITY)
            val channel = BusChannel(replyId, bus, channel, level)
            replyIds.add(replyId)
            channelsByReplyId[replyId] = channel
            channels.add(channel)
        }
        channelsByBus[bus] = channels
        return replyIds
    }

    fun clearBusChannels(bus: BusObject.AudioBus) {
        val channels = channelsByBus.remove(bus) ?: return
        for (ch in channels) {
            channelsByReplyId.remove(ch.replyId)
        }
    }

    override fun getDefault(): BusObject = getOutput()

    fun getOutput() = find { b -> b.busType == BusObject.Type.Output }
        ?: error("No output bus found in registry")

    fun getInput() = find { b -> b.busType == BusObject.Type.Input }
        ?: error("No output bus found in registry")

    fun filter(rate: Rate, channels: Int): List<BusObject> = filter { b ->
        b.rate == rate && b.channels.now == channels
    }

    private class BusChannel(
        val replyId: Int,
        private val bus: BusObject.AudioBus,
        private val channel: Int,
        val level: ReactiveVariable<Double>
    )

    companion object : PublicProperty<BusRegistry> by publicProperty("BusRegistry") {
        fun createDefault(): BusRegistry = BusRegistry(mutableListOf(BusObject.output, BusObject.input))

        private var replyIdCounter = 0
    }
}