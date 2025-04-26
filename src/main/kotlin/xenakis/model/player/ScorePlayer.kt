package xenakis.model.player

import reaktive.value.now
import xenakis.impl.*
import xenakis.model.Settings
import xenakis.model.player.ScoreEventCollector.Event
import xenakis.model.score.*
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.misc.PlayHead

class ScorePlayer(
    private val rootScore: Score,
    private val manager: PlaybackManager,
    override val client: SuperColliderClient,
    private val settings: Settings,
) : AbstractPlayer(DELTA_T, settings.lookAhead) {
    private var lastPlayFrom: Decimal = PlayHead.START

    override val playHead: PlayHead
        get() = manager.playHead

    override val loop get() = manager.loopingActivated.now

    override val maxTime: Decimal
        get() = rootScore.maxTime

    private fun activeObjects(time: Decimal, delta: Decimal): List<Event> {
        val dest = mutableListOf<Event>()
        collectActiveObjects(ObjectPosition(0.0, 0.0), rootScore, time, delta, dest)
        return dest
    }

    private fun collectActiveObjects(
        position: ObjectPosition, score: Score, time: Decimal, delta: Decimal,
        dest: MutableList<Event>,
    ) {
        if (position.time - delta > time) return
        for (inst in score.objectInstances) {
            val obj = inst.obj
            val absolutePosition = position + inst.position
            if (obj is ScoreObjectGroup) {
                collectActiveObjects(absolutePosition, obj.score, time, delta, dest)
            } else if (absolutePosition.time - delta <= time && time <= absolutePosition.time + obj.duration) {
                dest.add(Event(Event.Type.Dummy, absolutePosition, inst))
            }
        }
    }

    override fun startPlay(startFrom: Decimal): Boolean {
        client.sendAsync("start_play")
        manager.recorder.startingPlayback()
        Logger.fine("Starting playback at $startFrom", Logger.Category.Playback)
        lastPlayFrom = startFrom
        val activeObjects = activeObjects(startFrom, delta = settings.lookAhead)
        for ((_, position, inst) in activeObjects) {
            if (!inst.muted.now) {
                scheduleInstantly(inst, position)
            }
        }
        return true
    }

    fun scheduleInstantly(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        val delta = position.time - playHead.currentTime
        val pos = ObjectPosition(playHead.currentTime + delta.coerceAtLeast(zero), position.y)
        Logger.fine("Scheduling $obj at $pos, delta: $delta", Logger.Category.Playback)
        scheduleObject(obj, position, cutoff = -delta.coerceAtMost(zero))
    }

    fun stopPlayBackInstantly(obj: ScoreObject, pos: ObjectPosition) {
        val suffix = manager.activeObjects.remove(obj, pos) ?: return
        stopObject(obj, pos, suffix)
    }

    override fun scheduleEvents(t: Decimal, delta: Decimal) {
        for (ev in manager.events.eventsAt(t - delta, delta * 5)) {
            val (type, position, inst) = ev
            if (inst.muted.now) continue
            val obj = inst.obj
            when (type) {
                Event.Type.ObjectStart -> {
                    Logger.fine("ObjectStart: $obj at $position", Logger.Category.Playback)
                    scheduleObject(obj, position, cutoff = zero)
                }

                Event.Type.ObjectEnd -> {
                    Logger.fine("ObjectEnd: $obj at $position", Logger.Category.Playback)
                    val startPos = position + ObjectPosition(-obj.duration, zero)
                    if (obj.duration == zero) continue
                    val suffix = manager.activeObjects.remove(obj, startPos) ?: continue
                    stopObject(obj, startPos, suffix)
                }

                else -> {}
            }
            ev.scheduled = true
        }
    }

    private fun stopObject(obj: ScoreObject, startPos: ObjectPosition, suffix: Int ) {
        when (obj) {
            is SynthObject -> {
                try {
                    manager.graph.remove(obj, startPos, suffix)
                } catch (e: Exception) {
                    Logger.error("Failed to remove $obj from audio flow graph", e, Logger.Category.Playback)
                }
                val name = obj.superColliderName(suffix)
                client.run("if ($name != nil) { $name.free; }")
            }

            is ProcessObject, is TaskObject -> {
                val name = obj.superColliderName(suffix)
                client.run("$name.stop;")
            }

            else -> {}
        }
    }

    fun scheduleObject(obj: ScoreObject, absolutePosition: ObjectPosition, cutoff: Decimal) {
        try {
            if (!obj.validate()) return
        } catch (e: Exception) {
            Logger.error("Failed to validate $obj", e, Logger.Category.Playback)
            return
        }
        val time = absolutePosition.time - lastPlayFrom
        val timeForExecution = (time + settings.scLangLatency.now).toString()
        val suffix = try {
            manager.activeObjects.insert(obj, absolutePosition)
        } catch (e: Exception) {
            Logger.error("Failed to insert $obj into active object manager", e, Logger.Category.Playback)
            return
        }
        val placement = if (obj is SynthObject) {
            try {
                manager.graph.insert(obj, absolutePosition, suffix)
            } catch (e: Exception) {
                Logger.error("Failed to insert $obj into audio flow graph", e, Logger.Category.Playback)
                return
            }
        } else null
        val uniqueName = ActiveObjectManager.uniqueName(obj.name.now, suffix)
        val code = try {
            obj.writeCode(uniqueName, placement, cutoff.takeIf { it > zero } ?: zero)
        } catch (e: Exception) {
            Logger.error("Failed to write code for $obj", e, Logger.Category.Playback)
        }
        if (code == "") return
        try {
            client.send("schedule", listOf(timeForExecution, code))
        } catch (e: Exception) {
            Logger.error("Failed to schedule $obj", e, Logger.Category.Playback)
        }
        Logger.fine("unique name for $obj at $time: $uniqueName", Logger.Category.Playback)
        Logger.fine("time for execution: ${timeForExecution}s", Logger.Category.Playback)

    }

    override fun pausePlayback() {
        Logger.info("Pausing playback", Logger.Category.Playback)
        manager.activeObjects.forEach { activeObject ->
            if (activeObject is ActiveScoreObject) {
                stopObject(activeObject.obj, activeObject.absolutePosition, activeObject.suffix)
            }
        }
        manager.pausedPlayback()
        client.send("pause_play")
    }

    override fun resetPlayback() {
        if (manager.recorder.isActive.now) manager.recorder.stopRecording()
        pausePlayback()
        client.run("s.freeAll")
    }

    companion object {
        private val DELTA_T = 0.03.toDecimal()
    }
}