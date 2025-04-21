package xenakis.model.player

import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.flow.AudioFlows
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject

class ActiveObjectManager(private val flows: AudioFlows) {
    private val takenSuffixes = mutableMapOf<String, MutableSet<Int>>()
    private val suffixes = mutableMapOf<ScoreObject, MutableMap<ObjectPosition, Int>>()

    fun insert(obj: ScoreObject, absolutePosition: ObjectPosition): Int {
        val takenSuffixes = takenSuffixes[obj.name.now].orEmpty()
        val suffix = (0..Int.MAX_VALUE).first { n -> n !in takenSuffixes }
        suffixes.getOrPut(obj, ::mutableMapOf)[absolutePosition] = suffix
        return suffix
    }

    fun remove(obj: ScoreObject, absolutePosition: ObjectPosition): Int? {
        val suffix = suffixes[obj]?.remove(absolutePosition)
        takenSuffixes[obj.name.now]?.remove(suffix)
        if (suffix == null) {
            Logger.warn("could not remove $obj at $absolutePosition", Logger.Category.Playback)
        }
        return suffix
    }

    fun all(): List<ActiveObject> = suffixes.flatMap { (obj, suffixes) ->
        suffixes.map { (pos, suffix) -> ActiveScoreObject(obj, pos, suffix) }
    }

    fun forEach(action: (obj: ActiveObject) -> Unit) {
        suffixes.forEach { (obj, instances) ->
            instances.forEach { (pos, suffix) -> action(ActiveScoreObject(obj, pos, suffix)) }
        }
        for (flow in flows.all()) {
            if (flow.isActive.now) {
                action(ActiveAudioFlow(flow))
            }
        }
    }

    fun activeInstances(obj: ScoreObject): List<ActiveScoreObject> =
        suffixes[obj]?.map { (pos, suffix) -> ActiveScoreObject(obj, pos, suffix) }.orEmpty()

    fun clear() {
        takenSuffixes.clear()
        suffixes.clear()
    }

    companion object {
        fun uniqueName(base: String, suffix: Int) = when (suffix) {
            -1 -> "${base}___"
            0 -> base
            else -> "${base}_$suffix"
        }
    }
}