package ponticello.model.player

import bundles.PublicProperty
import bundles.publicProperty
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.model.flow.ActiveObjectNode
import ponticello.model.flow.AudioFlows
import ponticello.model.flow.NodeTree
import ponticello.model.obj.InstrumentReference
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.*
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.client.getArgument
import reaktive.value.now

class ActiveObjectsManager(private val context: Context) : OSCMessageListener {
    private val nextSuffix = mutableMapOf<ScoreObject, Int>()
    private val bySuffix = mutableMapOf<ScoreObject, MutableMap<Int, ActiveScoreObject>>()
    private val byAbsolutePosition = mutableMapOf<ScoreObject, MutableMap<ObjectPosition, ActiveScoreObject>>()

    fun insert(
        player: ScorePlayer, obj: ScoreObject, instance: ScoreObjectInstance?, absolutePosition: ObjectPosition,
        cutoff: Decimal,
        extraArguments: Map<ParameterDefObject, ParameterControl>,
    ): ActiveScoreObject {
        val suffix = nextSuffix[obj] ?: 0
        nextSuffix[obj] = suffix + 1
        val active = ActiveScoreObject(player, obj, instance, absolutePosition, suffix, cutoff, extraArguments)
        bySuffix.getOrPut(obj, ::mutableMapOf)[suffix] = active
        byAbsolutePosition.getOrPut(obj, ::mutableMapOf)[absolutePosition] = active
        return active
    }

    fun remove(obj: ScoreObject, absolutePosition: ObjectPosition): ActiveScoreObject? {
        val active = byAbsolutePosition[obj]?.get(absolutePosition) ?: return null
        remove(active)
        return active
    }

    fun all(): List<ActiveScoreObject> = bySuffix.flatMap { (_, map) -> map.values }

    fun forEach(action: (obj: ActiveObject) -> Unit) {
        bySuffix.forEach { (_, instances) ->
            instances.forEach { (_, active) -> action(active) }
        }
        for (group in context[AudioFlows].all()) {
            if (!group.isActive.now) continue
            for (flow in group.flows) {
                if (!flow.isActive.now) continue
                action(ActiveAudioFlow(flow))
            }
        }
    }

    fun activeInstances(obj: ScoreObject): List<ActiveScoreObject> =
        bySuffix[obj]?.map { (_, active) -> active }.orEmpty()

    fun clear(player: ScorePlayer) {
        for (active in all()) {
            if (active.player == player) remove(active)
        }
    }

    fun clear() {
        nextSuffix.clear()
        bySuffix.clear()
        byAbsolutePosition.clear()
    }

    private fun removeByName(uniqueName: String): ActiveObject? {
        val name = uniqueName.substringBeforeLast('_')
        val suffix = uniqueName.substringAfterLast('_').toIntOrNull() ?: return null
        val obj = context[ScoreObjectRegistry].getOrNull(name) ?: return null
        val active = bySuffix[obj]?.get(suffix) ?: return null
        remove(active)
        active.stopped()
        return active
    }

    private fun remove(active: ActiveScoreObject) {
        val obj = active.obj
        bySuffix[obj]?.remove(active.suffix)
        byAbsolutePosition[obj]?.remove(active.absolutePosition)
        if ((obj is SoundProcess && obj.def is SynthDefObject) ||
            (obj is LegacyMidiObject && obj.instrument.now is InstrumentReference.UserDefined)
        ) {
            val node = ActiveObjectNode(obj, active)
            context[NodeTree].removeNode(node)
        }
    }

    override fun acceptMessage(event: OSCMessageEvent) = ScorePlayer.execute {
        val address = event.message.address
        when (address) {
            "/stopped", "/freed" -> {
                val name = event.message.getArgument<String>(1, "name") ?: return@execute
                removeByName(name)
            }
        }
    }

    fun getActiveObject(obj: ScoreObject, pos: ObjectPosition): ActiveScoreObject? = byAbsolutePosition[obj]?.get(pos)

    companion object : PublicProperty<ActiveObjectsManager> by publicProperty("ActiveObjectsManager") {
        fun uniqueName(base: String, suffix: Int) = when (suffix) {
            -1 -> "${base}___"
            //0 -> base
            else -> "${base}_$suffix"
        }
    }
}