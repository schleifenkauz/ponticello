package ponticello.model.flow

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.OSCMessage
import hextant.context.Context
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.model.midi.VSTMidiInstrument
import ponticello.model.registry.IdProvider
import ponticello.model.registry.ObjectRegistry
import ponticello.model.score.ScoreObject
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument
import reaktive.value.now
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AudioFlows(override val objects: MutableList<AudioFlowGroup>) : ObjectRegistry<AudioFlowGroup>() {
    @Transient
    private var addedToServer = false

    val ids = IdProvider<AudioFlow>()

    override val objectType: String
        get() = "Flow group"

    override fun syncAll() {
        for (group in this) {
            group.sync()
        }
    }

    override fun initialize(context: Context) {
        context[AudioFlows] = this
        super.initialize(context)
        val client = context[SuperColliderClient]
        client.onTreeCleared {
            addedToServer = false
            createAllFlows()
        }
        client.addListener("/toggle_flow") { _, msg -> toggleFlow(msg) }
    }

    private fun toggleFlow(msg: OSCMessage) {
        val flowId = msg.getArgument<Int>(0, "Flow ID") ?: return
        val flow = ids.getById(flowId)
        if (flow == null) {
            Logger.warn("Received /toggle_flow for unknown flow ID: $flowId", Logger.Category.OSC)
            return
        }
        val group = flow.parentGroup ?: return
        if (!(group.isActive.now)) {
            group.toggleActive()
        }
        flow.toggleActive()
    }

    private fun createAllFlows() {
        if (addedToServer) return
        for (group in this) {
            if (!group.isActive.now) continue
            group.createGroupOnServer()
        }
        addedToServer = true
    }

    fun allFlows(): List<AudioFlow> = objects.flatMap { grp -> grp.flows } + midiVSTFlows()

    fun getFlow(name: String): AudioFlow? {
        for (grp in objects) {
            val flow = grp.flows.find { it.name.now == name }
            if (flow != null) return flow
        }
        return null
    }

    fun writeVSTPluginStates() {
        val futures = mutableListOf<CompletableFuture<String>>()
        for (flow in allFlows()) {
            if (flow !is VSTPluginFlow) continue
            futures.add(flow.saveConfiguration())
        }
        try {
            CompletableFuture.allOf(*futures.toTypedArray()).orTimeout(5, TimeUnit.SECONDS).join()
        } catch (e: TimeoutException) {
            Logger.error("Timeout writing VST plugin states", e, Logger.Category.VSTPlugins)
        }
    }

    fun allMidiTracks(): List<MidiTrackFlow> = flatMap { grp ->
        grp.flows.filterIsInstance<MidiTrackFlow>()
    }

    private fun midiVSTFlows() = allMidiTracks().flatMap { track ->
        track.instruments
            .filterIsInstance<VSTMidiInstrument>()
            .map { it.flow }
    }

    override fun onAdded(obj: AudioFlowGroup, idx: Int) {
        obj.flows.addListener(ids)
    }

    override fun onRemoved(obj: AudioFlowGroup, idx: Int) {
        for ((idx, flow) in obj.flows.withIndex().reversed()) {
            ids.removed(flow, idx)
        }
        obj.flows.removeListener(ids)
    }

    fun hasReferencesTo(obj: ScoreObject) = allFlows().any { flow -> flow.referencesScoreObject(obj) }

    companion object : PublicProperty<AudioFlows> by publicProperty("AudioFlows") {
        fun createDefault(): AudioFlows = AudioFlows(mutableListOf())
    }
}