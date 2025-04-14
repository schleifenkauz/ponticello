package xenakis.model.player

import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject

class SuffixManager {
    private val takenSuffixes = mutableMapOf<String, MutableSet<Int>>()
    private val suffixes = mutableMapOf<Pair<ScoreObject, ObjectPosition>, Int>()

    fun insert(obj: ScoreObject, absolutePosition: ObjectPosition): Int {
        val takenSuffixes = takenSuffixes[obj.name.now].orEmpty()
        val suffix = (0..Int.MAX_VALUE).first { n -> n !in takenSuffixes }
        suffixes[obj to absolutePosition] = suffix
        return suffix
    }

    fun remove(obj: ScoreObject, absolutePosition: ObjectPosition): Int? {
        val suffix = suffixes.remove(obj to absolutePosition)
        takenSuffixes[obj.name.now]?.remove(suffix)
        if (suffix == null) {
            Logger.warn("could not remove $obj at $absolutePosition", Logger.Category.Playback)
        }
        return suffix
    }

    fun clear() {
        takenSuffixes.clear()
        suffixes.clear()
    }

    companion object {
        fun uniqueName(base: String, suffix: Int) = if (suffix == 0) base else "${base}_$suffix"
    }
}