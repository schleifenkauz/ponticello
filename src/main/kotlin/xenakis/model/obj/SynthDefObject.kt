package xenakis.model.obj

import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.zero
import xenakis.model.flow.NodePlacement
import xenakis.model.player.ActiveAudioFlow
import xenakis.model.player.ActiveObjectsManager
import xenakis.model.player.ActiveScoreObject
import xenakis.model.player.ScorePlayer
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.SynthDefRegistry
import xenakis.sc.client.SuperColliderClient

@Serializable
sealed interface SynthDefObject : ParameterizedObjectDef, SuperColliderObject {
    val color: ReactiveVariable<Color>

    override val registry: ObjectRegistry<*>?
        get() = context[SynthDefRegistry]

    override val superColliderName: String
        get() = "\\${name.now}"

    override fun hasParameter(name: String): Boolean = name == "group" || super.hasParameter(name)

    fun onUpdated() {
        ScorePlayer.execute {
            context[ActiveObjectsManager].forEach { active ->
                val def = active.associatedDef
                if (def != this@SynthDefObject) return@forEach
                val placement = NodePlacement.replace(active.superColliderName)
                val code = when (active) {
                    is ActiveAudioFlow -> active.flow.writeCode(placement)
                    is ActiveScoreObject -> {
                        val cutoff = active.player.currentTime - active.absolutePosition.time
                        active.obj.writeCode(active.uniqueName, placement, cutoff, latency = zero)
                    }
                }
                context[SuperColliderClient].run(code)
            }
        }
    }

    companion object {
        val DATA_FORMAT = DataFormat("synth-def")
    }
}