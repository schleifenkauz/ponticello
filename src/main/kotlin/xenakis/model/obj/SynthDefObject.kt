package xenakis.model.obj

import hextant.context.Context
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.flow.AudioFlows
import xenakis.model.flow.NodePlacement
import xenakis.model.flow.SynthFlow
import xenakis.model.player.ActiveObjectManager
import xenakis.model.player.PlaybackManager
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.SynthDefRegistry
import xenakis.model.registry.reference
import xenakis.model.score.SynthObject
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
        for ((obj, pos, suffix) in context[PlaybackManager].activeObjects.all()) {
            if (obj !is SynthObject || obj.synthDef != this@SynthDefObject) continue
            val uniqueName = ActiveObjectManager.uniqueName(obj.name.now, suffix)
            val placement = NodePlacement(NodePlacement.AddAction.AddReplace, "~synth_$uniqueName")
            val cutoff = currentTime - pos.time
            val code = obj.writeCode(uniqueName, placement, cutoff)
            appendLine(code)
        }
        for (flow in context[AudioFlows].all()) {
            if (flow !is SynthFlow) continue
            if (flow.synthDef != this@SynthDefObject) continue
            val placement = NodePlacement(NodePlacement.AddAction.AddReplace, flow.superColliderName.now)
            flow.run { writeCode(placement) }
        }
    }

    companion object {
        val DATA_FORMAT = DataFormat("synth-def")
    }
}