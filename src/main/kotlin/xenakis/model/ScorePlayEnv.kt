package xenakis.model

import reaktive.value.now
import java.util.logging.Logger

class ScorePlayEnv(private val settings: Settings) {
    private val activeInstances = mutableMapOf<ActiveInstance, Int>()
    private val suffixes = mutableMapOf<ScoreObject, MutableSet<Int>>()

    val serverLatency: Double get() = settings.serverLatency.now
    val scLangLatency: Double get() = settings.scLangLatency.now
    val lookAhead: Double get() = scLangLatency + serverLatency

    private fun suffixes(obj: ScoreObject) = suffixes.getOrPut(obj) { mutableSetOf() }

    @Synchronized
    fun markStart(inst: ScoreObjectInstance, position: ObjectPosition): String {
        val obj = inst.obj
        val suffixes = suffixes(obj)
        val suffix = (0..Int.MAX_VALUE).first { n -> n !in suffixes }
        suffixes.add(suffix)
        val name = if (suffix == 0) obj.name.now else "${obj.name.now}_$suffix"
        activeInstances[ActiveInstance(inst, position, name)] = suffix
        logger.info("   marked start for $obj, suffix = $suffix")
        return name
    }

    @Synchronized
    fun markEnd(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        val element = ActiveInstance(inst, position, "<dummy>")
        val suffix = activeInstances.remove(element)
        if (suffix == null) {
            logger.severe("could not remove $element from")
            for (synth in activeInstances) {
                logger.severe("   $synth")
            }
        } else suffixes(obj).remove(suffix)
        logger.info("   marked end for $obj, suffix = $suffix")
    }

    @Synchronized
    fun getSynthOrderFor(group: ObjectReference, position: ObjectPosition): SynthOrder {
        val relevant = activeInstances.keys
            .filter { s ->
                val obj = s.inst.obj
                obj is SynthObject && obj.group.now == group && s.absolutePosition.time < position.time
            }
        val runBefore = relevant
            .filter { (_, pos) -> pos.y > position.y }
            .minByOrNull { (_, pos) -> pos.y }
        val runAfter = relevant
            .filter { (_, pos) -> pos.y < position.y }
            .maxByOrNull { (_, pos) -> pos.y }
        return when {
            runAfter != null -> SynthOrder("'addAfter'", "~synths['${runAfter.uniqueName}']")
            runBefore != null -> SynthOrder("'addBefore'", "~synths['${runBefore.uniqueName}']")
            else -> SynthOrder("'addToHead'", group.get<GroupObject>().superColliderName)
        }
    }

    @Synchronized
    fun clear() {
        activeInstances.clear()
        suffixes.clear()
    }

    fun activeInstances(inst: ScoreObjectInstance) = activeInstances.keys.filter { act -> act.inst == inst }

    fun activeInstances(obj: ScoreObject) = activeInstances.keys.filter { act -> act.inst.obj == obj }

    data class SynthOrder(val addAction: String, val target: String)

    data class ActiveInstance(
        val inst: ScoreObjectInstance,
        val absolutePosition: ObjectPosition,
        val uniqueName: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ActiveInstance) return false

            if (inst != other.inst) return false
            if (absolutePosition != other.absolutePosition) return false

            return true
        }

        override fun hashCode(): Int {
            var result = inst.hashCode()
            result = 31 * result + absolutePosition.hashCode()
            return result
        }
    }

    companion object {
        private val logger = Logger.getLogger("ScorePlayEnv")
    }
}