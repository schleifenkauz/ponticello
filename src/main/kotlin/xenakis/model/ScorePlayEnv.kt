package xenakis.model

import hextant.context.Context
import reaktive.value.now
import java.util.logging.Logger

class ScorePlayEnv(private val context: Context) {
    private val activeInstances = mutableSetOf<ActiveInstance>()
    private val suffixes = mutableMapOf<ScoreObject, Int>()

    val serverLatency: Double get() = context[Settings].serverLatency.now
    val scLangLatency: Double get() = context[Settings].scLangLatency.now
    val lookAhead: Double get() = scLangLatency + serverLatency

    @Synchronized
    fun markStart(inst: ScoreObjectInstance, position: ObjectPosition): String {
        val obj = inst.obj
        val suffix = (suffixes[obj] ?: -1) + 1
        suffixes[obj] = suffix
        val name = if (suffix == 0) obj.name.now else "${obj.name.now}_$suffix"
        if (obj is SynthObject) {
            activeInstances.add(ActiveInstance(inst, position, name))
        }
        logger.info("   marked start for $obj, suffix = ${suffixes[obj]}")
        return name
    }

    @Synchronized
    fun markEnd(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        if (obj is SynthObject) {
            val element = ActiveInstance(inst, position + ObjectPosition(-obj.duration, 0.0), "<dummy>")
            if (!activeInstances.remove(element)) {
                logger.severe("WARNING: could not remove $element from")
                for (synth in activeInstances) {
                    logger.severe("   $synth")
                }
            }
        }
        val suffix = suffixes[obj]
        if (suffix == null || suffix == -1) return //happens if we add objects under the cursors
        suffixes[obj] = suffix - 1
        logger.info("   marked end for $obj, suffix = ${suffixes[obj]}")
    }

    @Synchronized
    fun getSynthOrderFor(group: ObjectReference, position: ObjectPosition): SynthOrder {
        val relevant = activeInstances
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

    fun activeInstances(inst: ScoreObjectInstance) = activeInstances.filter { o -> o.inst == inst }

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