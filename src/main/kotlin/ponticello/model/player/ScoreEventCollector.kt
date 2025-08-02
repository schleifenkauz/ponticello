package ponticello.model.player

import javafx.geometry.Side
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.unaryMinus
import ponticello.impl.zero
import ponticello.model.GlobalSettings
import ponticello.model.score.*
import reaktive.value.now
import java.util.*

class ScoreEventCollector(
    private val rootScore: Score,
    private val settings: GlobalSettings,
    private val player: ScorePlayer? = null,
) : ScoreListener, ScoreObject.Listener {
    private val events = TreeMap<ObjectPosition, MutableSet<Event>>()
    private val scoreInstances = mutableMapOf<Score, MutableSet<ScoreObjectInstance>>()
    private val instances = mutableMapOf<ScoreObject, MutableSet<ScoreObjectInstance>>()

    private val scheduler = rootScore.context[ScoreObjectScheduler]

    init {
        rootScore.addListener(this)
    }

    private fun scoreInstances(score: Score) = scoreInstances.getOrPut(score) { mutableSetOf() }

    private fun instances(obj: ScoreObject) = instances.getOrPut(obj) { mutableSetOf() }

    private fun absolutePositions(score: Score): List<ObjectPosition> = when {
        score == rootScore -> listOf(ObjectPosition(0.0, 0.0))
        else -> scoreInstances(score).flatMap { inst ->
            if (inst.muted.now || inst.score == null) emptyList<ObjectPosition>()
            absolutePositions(inst.score!!).map { pos -> pos + inst.position }
        }
    }

    private fun absolutePositions(instance: ScoreObjectInstance) =
        instance.score?.let { absolutePositions(it).map { pos -> pos + instance.position } } ?: emptyList()

    private fun addEvent(event: Event) {
        events.getOrPut(event.absolutePosition) { mutableSetOf() }.add(event)
    }

    private fun removeEvent(event: Event) {
        val set = events[event.absolutePosition] ?: mutableSetOf()
        if (!set.remove(event)) {
            Logger.warn("Failed to remove $event", Logger.Category.Playback)
        }
    }

    override fun addedObject(score: Score, inst: ScoreObjectInstance, autoSelect: Boolean) = ScorePlayer.execute {
        if (inst.muted.now) return@execute
        added(inst)
        for (position in absolutePositions(inst)) {
            addToPlayback(inst, position)
        }
    }

    private fun added(inst: ScoreObjectInstance) {
        val obj = inst.obj
        if (instances(obj).add(inst) && instances(obj).size == 1) {
            obj.addListener(this)
        }
        if (obj is ScoreObjectGroup) {
            if (scoreInstances(obj.score).add(inst) && scoreInstances(obj.score).size == 1) {
                obj.score.addListener(this, notify = false)
                for (subInst in obj.score.objectInstances) {
                    added(subInst)
                }
            }
        }
    }

    override fun removedObject(score: Score, inst: ScoreObjectInstance) = ScorePlayer.execute {
        if (inst.muted.now) return@execute
        for (position in absolutePositions(inst)) {
            removeFromPlayback(inst, position)
        }
        removed(inst)
    }

    private fun removed(inst: ScoreObjectInstance) {
        val obj = inst.obj
        if (instances(obj).remove(inst) && instances(obj).isEmpty()) {
            obj.removeListener(this)
        }
        if (obj is ScoreObjectGroup) {
            if (scoreInstances(obj.score).remove(inst) && scoreInstances(obj.score).isEmpty()) {
                obj.score.removeListener(this)
                for (subInst in obj.score.objectInstances) {
                    removed(subInst)
                }
            }
        }
    }

    //TODO fix
    override fun finishedResize(obj: ScoreObject, deltaDuration: Decimal, deltaHeight: Decimal, side: Side) =
        ScorePlayer.execute {
            if (obj is ScoreObjectGroup) return@execute
            val eventsBefore = nEvents()
            val newEvents = mutableListOf<Event>()
            for ((_, events) in events) {
                val itr = events.iterator()
                for (ev in itr) {
                    if (ev.inst.obj != obj) continue
                    val (t, y) = ev.absolutePosition
                    val newY = if (side == Side.TOP) y - deltaHeight else y
                    if (ev.type == Event.Type.ObjectStart && (side == Side.LEFT || side == Side.TOP)) {
                        itr.remove()
                        val newStart = ObjectPosition(if (side == Side.LEFT) t - deltaDuration else t, newY)
                        newEvents.add(Event(Event.Type.ObjectStart, newStart, ev.inst))
                    } else if (ev.type == Event.Type.ObjectEnd && (side == Side.RIGHT || side == Side.TOP)) {
                        itr.remove()
                        val newEnd = ObjectPosition(if (side == Side.RIGHT) t + deltaDuration else t, newY)
                        newEvents.add(Event(Event.Type.ObjectEnd, newEnd, ev.inst))
                    }
                }
            }
            for (ev in newEvents) addEvent(ev)
            if (nEvents() != eventsBefore) {
                Logger.warn("Resizing object changed number of score events", Logger.Category.Playback)
            }
        }

    override fun movedObject(score: Score, inst: ScoreObjectInstance, dt: Decimal, dy: Decimal) = ScorePlayer.execute {
        if (inst.muted.now) return@execute
        val oldPosition = inst.position + ObjectPosition(-dt, -dy)
        val eventsBefore = nEvents()
        Logger.fine("Move object $inst from $oldPosition", Logger.Category.Playback)
        for (position in absolutePositions(score)) {
            removeFromPlayback(inst, position + oldPosition)
            addToPlayback(inst, position + inst.position)
        }
        if (nEvents() != eventsBefore) {
            Logger.warn("Moving object changed number of score events", Logger.Category.Playback)
        }
    }

    private fun nEvents() = events.values.sumOf { set -> set.size }

    override fun toggledMute(score: Score, inst: ScoreObjectInstance, muted: Boolean) = ScorePlayer.execute {
        if (!muted) added(inst)
        for (position in absolutePositions(inst)) {
            if (muted) removeFromPlayback(inst, position, onlyNonMuted = false)
            else addToPlayback(inst, position)
        }
        if (muted) removed(inst)
    }

    private fun addToPlayback(inst: ScoreObjectInstance, position: ObjectPosition) {
        if (inst.muted.now || inst.score == null) return
        val obj = inst.obj
        if (obj is ScoreObjectGroup) {
            Logger.fine(
                "Added sub score ${obj.name.now} to ${inst.score!!.scoreName.now} at ${inst.position}",
                Logger.Category.Playback
            )
            for (subInst in obj.score.objectInstances) {
                addToPlayback(subInst, position + subInst.position)
            }
        } else {
            Logger.fine("Added $inst at $position", Logger.Category.Playback)
            val posEnd = position + ObjectPosition(obj.duration, zero)
            val player = player
            if (
                player != null && player.isPlaying.now
                && player.playHead.currentTime in position.time - settings.lookAhead..posEnd.time
            ) {
                ScorePlayer.execute {
                    player.scheduleInstantly(inst, position)
                }
            }
            addEvent(Event(Event.Type.ObjectStart, position, inst))
            addEvent(Event(Event.Type.ObjectEnd, posEnd, inst))
        }
    }

    private fun removeFromPlayback(inst: ScoreObjectInstance, position: ObjectPosition, onlyNonMuted: Boolean = true) {
        if (onlyNonMuted && inst.muted.now) return
        val obj = inst.obj
        if (obj is ScoreObjectGroup) {
            for (subInst in obj.score.objectInstances) {
                removeFromPlayback(subInst, position + subInst.position)
            }
        } else {
            Logger.fine("Removing $inst at $position", Logger.Category.Playback)
            val posEnd = position + ObjectPosition(obj.duration, zero)
            removeEvent(Event(Event.Type.ObjectStart, position, inst))
            removeEvent(Event(Event.Type.ObjectEnd, posEnd, inst))
            val player = player
            if (player != null && player.isPlaying.now && player.playHead.currentTime in position.time..posEnd.time) {
                scheduler.stopPlayBackInstantly(obj, position)
            }
        }
    }

    //Only inside ScorePlayer.execute
    fun eventsAt(time: Decimal, delta: Decimal): List<Event> {
        val pos = ObjectPosition(time, zero)
        return events.tailMap(pos)
            .entries
            .takeWhile { (pos, _) -> pos.time < time + delta }
            .flatMap { (_, events) -> events.filter { ev -> !ev.scheduled } }
    }

    //Only inside ScorePlayer.execute
    fun resetEvents() {
        for ((_, events) in events) {
            for (ev in events) {
                ev.scheduled = false
            }
        }
    }

    fun recomputeEvents() {
        removeListeners()
        events.clear()
        instances.clear()
        scoreInstances.clear()
        rootScore.addListener(this)
    }

    private fun removeListeners() {
        rootScore.removeListener(this)
        for ((score, instances) in scoreInstances) {
            if (instances.isNotEmpty()) score.removeListener(this)
        }
        for ((obj, instances) in instances) {
            if (instances.isNotEmpty()) obj.removeListener(this)
        }
    }

    data class Event(
        val type: Type,
        val absolutePosition: ObjectPosition,
        val inst: ScoreObjectInstance,
        var scheduled: Boolean = false,
    ) {
        override fun toString(): String = "$type: ${inst.obj.name.now} at $absolutePosition"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Event) return false

            if (type != other.type) return false
            //if (absolutePosition != other.absolutePosition) return false
            if (inst.obj.name.now != other.inst.obj.name.now) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            //result = 31 * result + absolutePosition.hashCode()
            result = 31 * result + inst.obj.name.now.hashCode()
            return result
        }

        enum class Type {
            Dummy, ObjectStart, ObjectEnd;
        }
    }
}