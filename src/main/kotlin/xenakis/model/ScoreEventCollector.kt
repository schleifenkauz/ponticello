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

    private fun absolutePositions(instance: ScoreObjectInstance) =
        absolutePositions(instance.score).map { pos -> pos + instance.position }

    @Synchronized
    override fun addedObject(score: Score, inst: ScoreObjectInstance) {
        if (inst.muted) return
        for (position in absolutePositions(inst)) {
            addToPlayback(inst, position)
        }
    }

    @Synchronized
    override fun removedObject(score: Score, inst: ScoreObjectInstance) {
        if (inst.muted) return
        for (position in absolutePositions(inst)) {
            removeFromPlayback(inst, position)
        }
    }

    private fun addToPlayback(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        if (obj is ScoreObjectGroup) {
            logger.info("Added sub score ${obj.name.now} to ${inst.score.scoreName.now} at ${inst.position}")
            scoreInstances(obj.score).add(inst)
            if (scoreInstances(obj.score).size == 1) {
                obj.score.addListener(this) //this will recurse into [addToPlayBack] via [addedObject]
            } else {
                for (subInst in obj.score.objectInstances) {
                    addToPlayback(subInst, position + subInst.position)
                }
            }
        } else {
            logger.info("Added $inst to ${inst.score.scoreName.now} at $position")
            val posEnd = position + ObjectPosition(obj.duration, 0.0)
            if (player != null && env != null && player.isPlaying && player.currentTime in position.time - env.lookAhead..posEnd.time) {
                player.scheduleInstantly(inst, position)
            }
            events.add(Event(Event.Type.ObjectStart, position, inst))
            events.add(Event(Event.Type.ObjectEnd, posEnd, inst))
        }
    }

    private fun removeFromPlayback(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        if (obj is ScoreObjectGroup) {
            scoreInstances(obj.score).remove(inst)
            if (scoreInstances(obj.score).isEmpty()) obj.score.removeListener(this)
            for (subInst in obj.score.objectInstances) {
                removeFromPlayback(subInst, position + subInst.position)
            }
        } else {
            logger.info("Removed $inst from ${inst.score.scoreName.now} at $position")
            val posEnd = position + ObjectPosition(obj.duration, 0.0)
            events.remove(Event(Event.Type.ObjectStart, position, inst))
            events.remove(Event(Event.Type.ObjectEnd, posEnd, inst))
            if (player != null && env != null && player.isPlaying) {
                for ((activeInstance, pos, name) in env.activeInstances(inst)) {
                    if (pos == position) player.stopPlayBackInstantly(activeInstance, pos, name)
                }
            }
        }
    }

    @Synchronized
    override fun movedObject(score: Score, inst: ScoreObjectInstance, oldPosition: ObjectPosition) {
        if (inst.muted) return
        for (position in absolutePositions(inst.score)) {
            removeFromPlayback(inst, position + oldPosition)
            addToPlayback(inst, position + inst.position)
        }
    }

    @Synchronized
    override fun toggledMute(score: Score, inst: ScoreObjectInstance, muted: Boolean) {
        for (position in absolutePositions(inst)) {
            if (muted) removeFromPlayback(inst, position)
            else addToPlayback(inst, position)
        }
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