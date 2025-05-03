package xenakis.model.player

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.flow.AudioFlows
import xenakis.model.flow.NodeTree
import xenakis.model.flow.SynthObjectNode
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject
import xenakis.model.score.SynthObject
import xenakis.sc.client.SuperColliderListener

class ActiveObjectsManager(private val context: Context) : SuperColliderListener {
    private val takenSuffixes = mutableMapOf<String, MutableSet<Int>>()
    private val bySuffix = mutableMapOf<ScoreObject, MutableMap<Int, ActiveScoreObject>>()
    private val byAbsolutePosition = mutableMapOf<ScoreObject, MutableMap<ObjectPosition, ActiveScoreObject>>()

    fun insert(player: ScorePlayer, obj: ScoreObject, absolutePosition: ObjectPosition): ActiveScoreObject {
        val takenSuffixes = takenSuffixes[obj.name.now].orEmpty()
        val suffix = (0..Int.MAX_VALUE).first { n -> n !in takenSuffixes }
        val active = ActiveScoreObject(player, obj, absolutePosition, suffix)
        bySuffix.getOrPut(obj, ::mutableMapOf)[suffix] = active
        byAbsolutePosition.getOrPut(obj, ::mutableMapOf)[absolutePosition] = active
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

    private fun removeByName(uniqueName: String): ActiveObject? {
        val name = uniqueName.substringBeforeLast('_')
        val suffix = uniqueName.substringAfterLast('_').toIntOrNull() ?: return null
        val obj = context[ScoreObjectRegistry].getOrNull(name) ?: return null
        val active = bySuffix[obj]?.get(suffix) ?: return null
        remove(active)
        println("Removed $uniqueName")
        return active
    }

    private fun remove(active: ActiveScoreObject) {
        val obj = active.obj
        bySuffix[obj]?.remove(active.suffix)
        byAbsolutePosition[obj]?.remove(active.absolutePosition)
        takenSuffixes[name]?.remove(active.suffix)
        if (obj is SynthObject) {
            val node = SynthObjectNode(obj, active)
            context[NodeTree].removeNode(node)
        }
    }

    override fun onMessage(path: String, content: String) = ScorePlayer.execute {
        when {
            path.startsWith("/freed") || path.startsWith("/stopped") -> {
                val activeObj = removeByName(content)
                if (activeObj == null) {
                    Logger.warn("Received '$path' message for unknown object: $content", Logger.Category.Playback)
                } else {
                    Logger.fine("Received '$path' message for $activeObj", Logger.Category.Playback)
                }
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