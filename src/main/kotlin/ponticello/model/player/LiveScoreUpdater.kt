package ponticello.model.player

import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.unaryMinus
import ponticello.impl.zero
import ponticello.model.score.*
import reaktive.value.now

class LiveScoreUpdater(
    private val rootScore: Score,
    private val player: ScorePlayer? = null,
) : ScoreListener, ScoreObject.Listener {
    private val instances = mutableMapOf<ScoreObject, MutableSet<ScoreObjectInstance>>()

    private val scheduler = rootScore.context[ScoreObjectScheduler]

    private fun scoreInstances(score: Score): MutableSet<ScoreObjectInstance> {
        val obj = score.parentObject ?: error("$score has no parent object")
        return instances(obj)
    }

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
            if (obj is AbstractScoreObjectGroup) {
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
            if (obj is AbstractScoreObjectGroup) {
                obj.score.removeListener(this)
                for (subInst in obj.score.objectInstances) {
                    removed(subInst)
                }
            }
        }
    }

    override fun movedObject(score: Score, inst: ScoreObjectInstance, dt: Decimal, dy: Decimal) = ScorePlayer.execute {
        if (inst.muted.now) return@execute
        val oldPosition = inst.position + ObjectPosition(-dt, -dy)
        Logger.fine("Move object $inst from $oldPosition", Logger.Category.Playback)
        for (position in absolutePositions(score)) {
            removeFromPlayback(inst, position + oldPosition)
            addToPlayback(inst, position + inst.position)
        }
    }

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
                && player.playHead.currentTime in position.time - player.lookAhead..posEnd.time
            ) {
                ScorePlayer.execute {
                    player.scheduleInstantly(inst, position)
                }
            }
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
            val player = player
            if (player != null && player.isPlaying.now && player.playHead.currentTime in position.time..posEnd.time) {
                scheduler.stopObjectInstantly(obj, position)
            }
        }
    }
}