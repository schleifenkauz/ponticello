package ponticello.model.obj

import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import ponticello.impl.zero
import ponticello.model.flow.NodePlacement
import ponticello.model.player.ActiveAudioFlow
import ponticello.model.player.ActiveObjectsManager
import ponticello.model.player.ActiveScoreObject
import ponticello.model.player.ScorePlayer
import ponticello.model.registry.ObjectRegistry
import ponticello.model.registry.SynthDefRegistry
import ponticello.sc.client.SuperColliderClient

@Serializable
sealed interface SynthDefObject : ParameterizedObjectDef, SuperColliderObject {
    val color: ReactiveVariable<Color>

    override val registry: ObjectRegistry<*>?
        get() = context[SynthDefRegistry]

    override val superColliderName: String
        get() = "\\${name.now}"

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
                        active.obj.writeCode(active.uniqueName, placement, cutoff, latency = zero, active.extraArguments)
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