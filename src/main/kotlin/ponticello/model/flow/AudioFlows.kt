package ponticello.model.flow

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.client.SuperColliderClient
import reaktive.value.now
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

class AudioFlows(override val objects: MutableList<AudioFlowGroup>) : ObjectRegistry<AudioFlowGroup>() {
    @Transient
    private var addedToServer = false

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
        context[SuperColliderClient].onTreeCleared {
            context[NodeTree].clear()
            addedToServer = false
            createAllFlows()
        }
    }

    fun createAllFlows() {
        if (addedToServer) return
        for (group in this) {
            if (!group.isActive.now) continue
            group.createFlows()
        }
        addedToServer = true
    }

    fun allFlows() = objects.flatMap { grp -> grp.flows }

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
            CompletableFuture.allOf(*futures.toTypedArray()).join()
        } catch (e: TimeoutException) {
            Logger.error("Timeout writing VST plugin states", e, Logger.Category.VSTPlugins)
        }
    }

    companion object : PublicProperty<AudioFlows> by publicProperty("AudioFlows") {
        fun createDefault(): AudioFlows = AudioFlows(mutableListOf())
    }
}