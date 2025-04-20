package xenakis.model.player

import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject

class ActiveObjectManager {
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
        suffixes.map { (pos, suffix) -> ActiveObject(obj, pos, suffix) }
    }

    fun forEach(action: (obj: ScoreObject, absolutePosition: ObjectPosition, suffix: Int) -> Unit) {
        suffixes.forEach { (obj, instances) ->
            instances.forEach { (pos, suffix) -> action(obj, pos, suffix) }
        }
    }

    fun activeInstances(obj: ScoreObject): List<ActiveObject> =
        suffixes[obj]?.map { (pos, suffix) -> ActiveObject(obj, pos, suffix) }.orEmpty()

    fun clear() {
        takenSuffixes.clear()
        suffixes.clear()
    }

    data class ActiveObject(val obj: ScoreObject, val absolutePosition: ObjectPosition, val suffix: Int)

    companion object {
        fun uniqueName(base: String, suffix: Int) = when (suffix) {
            -1 -> "${base}___"
            0 -> base
            else -> "${base}_$suffix"
        }
    }
}