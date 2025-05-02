package xenakis.model.flow

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import reaktive.value.now
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.client.SuperColliderClient
import java.io.Serializable

class AudioFlows(override val objects: MutableList<AudioFlowGroup>, ) : ObjectRegistry<AudioFlowGroup>() {
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
        context[SuperColliderClient].onTreeCleared { createAllFlows() }
    }

    fun createAllFlows() {
        context[NodeTree].clear()
        for (group in this) {
            if (!group.isActive.now) continue
            group.createFlows()
        }
    }

    data class FlowReference(val groupName: String, val flowName: String): Serializable {
        fun getFlow(flows: AudioFlows) = flows.getOrNull(groupName)?.flows?.getOrNull(flowName)

        fun removeFrom(flows: AudioFlows) {
            flows.getOrNull(groupName)?.flows?.removeByName(flowName)
        }
    }

    companion object : PublicProperty<AudioFlows> by publicProperty("AudioFlows") {
        fun createDefault(): AudioFlows = AudioFlows(mutableListOf())
    }
}