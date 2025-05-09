package xenakis.model.player

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import reaktive.value.now
import xenakis.model.flow.AudioFlows
import xenakis.model.flow.NodeTree
import xenakis.model.flow.SynthObjectNode
import xenakis.model.obj.ParameterDefObject
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject
import xenakis.model.score.SynthObject
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.client.SuperColliderListener

class ActiveObjectsManager(private val context: Context) : SuperColliderListener {
    private val nextSuffix = mutableMapOf<ScoreObject, Int>()
    private val bySuffix = mutableMapOf<ScoreObject, MutableMap<Int, ActiveScoreObject>>()
    private val byAbsolutePosition = mutableMapOf<ScoreObject, MutableMap<ObjectPosition, ActiveScoreObject>>()

    fun insert(
        player: ScorePlayer, obj: ScoreObject, absolutePosition: ObjectPosition,
        extraArguments: Map<ParameterDefObject, ParameterControl>
    ): ActiveScoreObject {
        val suffix = nextSuffix[obj] ?: 0
        nextSuffix[obj] = suffix + 1
        val active = ActiveScoreObject(player, obj, absolutePosition, suffix, extraArguments)
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
        if (obj is SynthObject) {
            val node = SynthObjectNode(obj, active)
            context[NodeTree].removeNode(node)
        }
    }

    override fun onMessage(path: String, content: String) = ScorePlayer.execute {
        when {
            path.startsWith("/freed") || path.startsWith("/stopped") -> {
                println("Received '$path' message with content '$content'")
                removeByName(content)
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