package xenakis.model.obj

import hextant.context.Context
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.flow.NodePlacement
import xenakis.model.player.ActiveAudioFlow
import xenakis.model.player.ActiveLiveObject
import xenakis.model.player.ActiveScoreObject
import xenakis.model.player.PlaybackManager
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.SynthDefRegistry
import xenakis.model.registry.reference
import xenakis.model.score.controls.GroupControl
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.GroupControlSpec
import xenakis.sc.client.SuperColliderClient

@Serializable
sealed interface SynthDefObject : ParameterizedObjectDef, SuperColliderObject {
    val color: ReactiveVariable<Color>

    override val registry: ObjectRegistry<*>?
        get() = context[SynthDefRegistry]

    override val superColliderName: String
        get() = "\\${name.now}"

    override fun getParameter(name: String): ParameterDefObject? = when (name) {
        "group" -> ParameterDefObject("group", GroupControlSpec)
        else -> super.getParameter(name)
    }

    override fun hasParameter(name: String): Boolean = name == "group" || super.hasParameter(name)

    override fun defaultControls(
        context: Context, defaultGroup: GroupReference?, defaultBus: BusReference?,
    ): MutableList<Pair<String, ParameterControl>> {
        val controls = super.defaultControls(context, defaultGroup, defaultBus)
        val group = defaultGroup ?: context[GroupRegistry].getDefault().reference()
        controls.add("group" to GroupControl(reactiveVariable(group)))
        return controls
    }

    fun onUpdated() = context[SuperColliderClient].run {
        val currentTime = context[PlaybackManager].playHead.currentTime
        context[PlaybackManager].activeObjects.forEach { active ->
            val def = active.associatedDef
            if (def != this@SynthDefObject) return@forEach
            val uniqueName = active.uniqueName
            val placement = NodePlacement(NodePlacement.AddAction.AddReplace, active.superColliderName)
            when (active) {
                is ActiveAudioFlow -> active.flow.run { writeCode(placement) }
                is ActiveScoreObject -> {
                    val cutoff = currentTime - active.absolutePosition.time
                    val code = active.obj.writeCode(uniqueName, placement, cutoff)
                    appendLine(code)
                }

                is ActiveLiveObject -> TODO()
            }
        }
    }

    companion object {
        val DATA_FORMAT = DataFormat("synth-def")
    }
}