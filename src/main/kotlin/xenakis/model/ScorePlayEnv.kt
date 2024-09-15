package xenakis.model

import reaktive.value.now

class ScorePlayEnv {
    private val activeSynths = mutableSetOf<ActiveSynth>()
    private val suffixes = mutableMapOf<ScoreObject, Int>()

    fun markStart(obj: ScoreObject, position: ObjectPosition) {
        if (obj is SynthObject) {
            activeSynths.add(ActiveSynth(obj, position))
        }
        suffixes[obj] = (suffixes[obj] ?: -1) + 1
    }

    fun markEnd(obj: ScoreObject, position: ObjectPosition) {
        if (obj is SynthObject) {
            activeSynths.remove(ActiveSynth(obj, position + ObjectPosition(-obj.duration, 0.0)))
        }
        val suffix = suffixes[obj] ?: error("Start of $obj was not marked.")
        suffixes[obj] = suffix - 1
    }

    fun getUniqueNameFor(obj: ScoreObject): String {
        val suffix = suffixes[obj] ?: error("No suffix for $obj generated yet.")
        return if (suffix == 0) obj.name.now else "${obj.name.now}_$suffix"
    }

    fun getSynthOrderFor(group: ObjectReference, position: ObjectPosition): SynthOrder {
        val relevant = activeSynths.filter { s -> s.obj.group.now == group && s.absolutePosition.time < position.time }
        val runBefore = relevant
            .filter { (_, pos) -> pos.y > position.y }
            .minByOrNull { (_, pos) -> pos.y }
        val runAfter = relevant
            .filter { (_, pos) -> pos.y < position.y }
            .maxByOrNull { (_, pos) -> pos.y }
        return when {
            runAfter != null -> SynthOrder("'addAfter'", "~synths['${runAfter.obj.name.now}']")
            runBefore != null -> SynthOrder("'addBefore'", "~synths['${runBefore.obj.name.now}']")
            else -> SynthOrder("'addToHead'", group.get<GroupObject>().superColliderName)
        }
    }

    fun clear() {
        activeSynths.clear()
        suffixes.clear()
    }

    data class SynthOrder(val addAction: String, val target: String)

    data class ActiveSynth(val obj: SynthObject, val absolutePosition: ObjectPosition)
}