package ponticello.model.server

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.OSCMessage
import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.model.flow.NodePlacement
import ponticello.model.instr.BusObject
import ponticello.model.obj.SuperColliderObject
import ponticello.model.registry.SuperColliderObjectRegistry
import ponticello.sc.Rate
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument
import ponticello.sc.client.run
import reaktive.event.Event
import reaktive.event.EventStream
import reaktive.event.event
import reaktive.value.now

@Serializable
class BusRegistry(
    override val objects: MutableList<BusObject>,
) : SuperColliderObjectRegistry<BusObject>() {
    override val objectType: String
        get() = "Bus"

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.ServerBoot

    @Transient
    private var replyIdCounter = 0

    @Transient
    private val levelEvents = mutableMapOf<Int, Event<List<Double>>>()

    @Transient
    private val availableLevelSendSynthDefs = mutableSetOf<Int>()

    override fun initialize(context: Context) {
        context[BusRegistry] = this
        super.initialize(context)
        client.onTreeCleared(initially = true) {
            client.run {
                for (bus in filterIsInstance<BusObject.ControlBus>()) {
                    bus.run { setDefaultValue(skipIfZero = false) }
                }
                +"~group_level_send = Group.tail(Server.local)"
                val placement = NodePlacement.tail("~group_level_send")
                for (bus in filterIsInstance<BusObject.AudioBus>()) {
                    createLevelSendSynth(this, bus, placement)
                }
            }
        }
        context[SuperColliderClient].addListener("/bus_levels") { _, msg -> receivedBusLevel(msg) }
    }

    fun reserveReplyId(): Int {
        val id = replyIdCounter++
        levelEvents[id] = event()
        return id
    }

    private fun createLevelSendSynth(writer: ScWriter, bus: BusObject.AudioBus, placement: NodePlacement) {
        if (bus.replyId !in levelEvents) {
            levelEvents[bus.replyId] = event()
        }
        createLevelSendSynth(writer, bus, placement, bus.replyId, "~level_send_${bus.name.now}")
    }

    fun createLevelSendSynth(
        writer: ScWriter, bus: BusObject.AudioBus,
        placement: NodePlacement, id: Int, synthVar: String
    ) = writer.run {
        val channels = bus.channels.now
        if (channels !in availableLevelSendSynthDefs) {
            +"~addLevelSendSynthDef.($channels)"
            availableLevelSendSynthDefs.add(channels)
        }
        +"Server.local.sync"
        val args = "[bus: ${bus.superColliderName}, id: $id, rate: 10, lag: 0.05]"
        +"$synthVar = Synth(\\level_send_$channels, $args, ${placement.code})"
    }

    override fun onAdded(obj: BusObject, idx: Int) {
        super.onAdded(obj, idx)
        if (obj is BusObject.AudioBus) {
            val placement = NodePlacement.tail("~group_level_send")
            createLevelSendSynth(obj, placement)
        }
    }

    fun createLevelSendSynth(obj: BusObject.AudioBus, placement: NodePlacement) {
        client.run {
            createLevelSendSynth(this, obj, placement)
        }
    }

    private fun receivedBusLevel(msg: OSCMessage) {
        val replyId = msg.getArgument<Int>(0, "replyID") ?: return
        val levels = msg.arguments.drop(1).map { lvl -> (lvl as Float).toDouble() }
        val event = levelEvents[replyId]
        if (event == null) {
            Logger.warn(
                "Received bus_level message: Event with replyID '$replyId' not found.",
                Logger.Category.SuperCollider
            )
            return
        }
        event.fire(levels)
    }

    fun levels(replyId: Int): EventStream<List<Double>> = levelEvents.getValue(replyId).stream

    fun levels(bus: BusObject.AudioBus): EventStream<List<Double>> = levels(bus.replyId)

    override fun getDefault(): BusObject = getOutput()

    fun getOutput() = find { b -> b.busType == BusObject.Type.Output }
        ?: error("No output bus found in registry")

    fun getInput() = find { b -> b.busType == BusObject.Type.Input }
        ?: error("No output bus found in registry")

    fun filter(rate: Rate, channels: Int): List<BusObject> = filter { b ->
        b.rate == rate && b.channels.now == channels
    }

    companion object : PublicProperty<BusRegistry> by publicProperty("BusRegistry") {
        fun createDefault(): BusRegistry = BusRegistry(mutableListOf(BusObject.output, BusObject.input))
    }
}