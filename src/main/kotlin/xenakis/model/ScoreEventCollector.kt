package xenakis.model

import reaktive.value.now
import java.util.*
import java.util.logging.Logger

class ScoreEventCollector(
    private val rootScore: Score,
    private val player: ScorePlayer?,
    private val env: ScorePlayEnv?
) : ScoreListener {
    private val events = TreeSet<Event>()
    private val scoreInstances = mutableMapOf<Score, MutableSet<ScoreObjectInstance>>()

    init {
        rootScore.addListener(this)
    }

    private fun scoreInstances(score: Score) = scoreInstances.getOrPut(score) { mutableSetOf() }

    private fun absolutePositions(score: Score): List<ObjectPosition> = when {
        score == rootScore -> listOf(ObjectPosition(0.0, 0.0))
        else -> scoreInstances(score).flatMap { inst ->
            absolutePositions(inst.score).map { pos -> pos + inst.position }
        }
    }

    @Synchronized
    override fun addedObject(score: Score, inst: ScoreObjectInstance) {
        if (inst.muted) return
        addToPlayback(inst, score)
    }

    @Synchronized
    override fun removedObject(score: Score, inst: ScoreObjectInstance) {
        if (inst.muted) return
        removeFromPlayback(inst)
    }

    private fun addToPlayback(inst: ScoreObjectInstance, score: Score) {
        val obj = inst.obj
        if (obj is ScoreObjectGroup) {
            logger.info("Added sub score ${obj.name.now} to ${score.scoreName.now} at ${inst.position}")
            scoreInstances(obj.score).add(inst)
            obj.score.addListener(this)
        } else {
            logger.info("Added $inst to ${score.scoreName.now}")
            for (pos in absolutePositions(score)) {
                val posStart = pos + inst.position
                val posEnd = posStart + ObjectPosition(obj.duration, 0.0)
                logger.info("   at $posStart")
                if (player != null && env != null && player.isPlaying && player.currentTime in posStart.time - env.lookAhead..posEnd.time) {
                    player.scheduleInstantly(inst, posStart)
                }
                events.add(Event(Event.Type.ObjectStart, posStart, inst))
                events.add(Event(Event.Type.ObjectEnd, posEnd, inst))
            }
        }
    }

    private fun removeFromPlayback(inst: ScoreObjectInstance) {
        val obj = inst.obj
        val itr = events.iterator()
        for (ev in itr) {
            if (ev.inst == inst) {
                itr.remove()
                logger.info("Removed ${ev.type}: ${ev.inst} at ${ev.absolutePosition}")
            }
        }
        if (obj is ScoreObjectGroup) {
            scoreInstances(obj.score).remove(inst)
            for (subInst in obj.score.objectInstances) {
                removedObject(obj.score, subInst)
            }
            obj.score.removeListener(this)
        } else if (player != null && env != null && player.isPlaying) {
            for ((activeInstance, pos, name) in env.activeInstances(inst)) {
                env.markEnd(activeInstance, pos)
                val endPos = pos + ObjectPosition(activeInstance.obj.duration, 0.0)
                events.remove(Event(Event.Type.ObjectEnd, endPos, activeInstance))
                player.stopPlayBackInstantly(activeInstance, name)
            }
        }
    }

    @Synchronized
    override fun movedObject(score: Score, inst: ScoreObjectInstance) {
        if (inst.muted) return
        removeFromPlayback(inst)
        addToPlayback(inst, score)
    }

    @Synchronized
    override fun toggledMute(score: Score, inst: ScoreObjectInstance, muted: Boolean) {
        if (muted) removeFromPlayback(inst)
        else addToPlayback(inst, score)
    }

    @Synchronized
    fun eventsAt(time: Double, delta: Double): List<Event> {
        val dummy = Event(Event.Type.Dummy, ObjectPosition(time, 0.0), null)
        return events.tailSet(dummy).takeWhile { ev -> ev.absolutePosition.time < time + delta }
    }

    data class Event(
        val type: Type,
        val absolutePosition: ObjectPosition,
        val inst: ScoreObjectInstance?
    ) : Comparable<Event> {
        override fun compareTo(other: Event): Int = compareValuesBy(this, other, Event::absolutePosition)

        override fun toString(): String = "$type: ${inst?.obj?.name?.now} at $absolutePosition"

        enum class Type {
            Dummy, ObjectStart, ObjectEnd;
        }
    }

    companion object {
        private val logger = Logger.getLogger("ScoreEventCollector")
    }
}