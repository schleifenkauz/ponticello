package ponticello.model.obj

import kotlinx.serialization.Serializable
import ponticello.impl.zero
import ponticello.model.flow.NodePlacement
import ponticello.model.player.ActiveAudioFlow
import ponticello.model.player.ActiveObjectsManager
import ponticello.model.player.ActiveScoreObject
import ponticello.model.player.ScorePlayer
import ponticello.model.registry.InstrumentRegistry
import ponticello.model.registry.ObjectRegistry
import ponticello.model.registry.reference
import ponticello.sc.client.SuperColliderClient
import reaktive.value.now

@Serializable
sealed interface SynthDefObject : InstrumentObject, SuperColliderObject {
    override val registry: ObjectRegistry<*>?
        get() = context[InstrumentRegistry]

    override val superColliderName: String
        get() = "\\${name.now}"

    override fun onUpdated() {
        ScorePlayer.execute {
            context[ActiveObjectsManager].forEach { active ->
                val def = active.associatedDef
                if (def != this@SynthDefObject) return@forEach
                val placement = NodePlacement.replace(active.superColliderName)
                val code = when (active) {
                    is ActiveAudioFlow -> active.flow.writeCode(placement)
                    is ActiveScoreObject -> {
                        val cutoff = active.player.currentTime - active.absolutePosition.time
                        active.obj.writeCode(
                            active.instance,
                            active.uniqueName,
                            placement,
                            cutoff,
                            latency = zero,
                            active.extraArguments
                        )
                    }
                }
                context[SuperColliderClient].run(code)
            }
        }
    }

    override fun instrumentReference(): InstrumentReference = InstrumentReference.UserDefined(this.reference())
}