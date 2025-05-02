package xenakis.model.player

import bundles.PublicProperty
import bundles.publicProperty
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.flow.AudioFlows
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject

class ActiveObjectsManager(private val flowGroups: AudioFlows) {
    private val takenSuffixes = mutableMapOf<String, MutableSet<Int>>()
    private val suffixes = mutableMapOf<ScoreObject, MutableMap<ObjectPosition, ActiveScoreObject>>()

    fun insert(player: ScorePlayer, obj: ScoreObject, absolutePosition: ObjectPosition): Int {
        val takenSuffixes = takenSuffixes[obj.name.now].orEmpty()
        val suffix = (0..Int.MAX_VALUE).first { n -> n !in takenSuffixes }
        val active = ActiveScoreObject(player, obj, absolutePosition, suffix)
        suffixes.getOrPut(obj, ::mutableMapOf)[absolutePosition] = active
        return suffix
    }

    fun remove(obj: ScoreObject, absolutePosition: ObjectPosition): Int? {
        val active = suffixes[obj]?.remove(absolutePosition)
        if (active == null) {
            Logger.warn("could not remove $obj at $absolutePosition", Logger.Category.Playback)
            return null
        }
        takenSuffixes[obj.name.now]?.remove(active.suffix)
        return active.suffix
    }

    fun all(): List<ActiveScoreObject> = suffixes.flatMap { (_, suffixes) ->
        suffixes.map { (_, active) -> active }
    }

    fun forEach(action: (obj: ActiveObject) -> Unit) {
        suffixes.forEach { (_, instances) ->
            instances.forEach { (_, active) -> action(active) }
        }
        for (group in flowGroups.all()) {
            if (!group.isActive.now) continue
            for (flow in group.flows) {
                if (!flow.isActive.now) continue
                action(ActiveAudioFlow(flow))
            }
        }
    }

    fun activeInstances(obj: ScoreObject): List<ActiveScoreObject> =
        suffixes[obj]?.map { (_, active) -> active }.orEmpty()

    fun clear() {
        takenSuffixes.clear()
        suffixes.clear()
    }

    companion object: PublicProperty<ActiveObjectsManager> by publicProperty("ActiveObjectsManager") {
        fun uniqueName(base: String, suffix: Int) = when (suffix) {
            -1 -> "${base}___"
            0 -> base
            else -> "${base}_$suffix"
        }
    }
}