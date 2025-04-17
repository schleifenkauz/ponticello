package xenakis.model.player

import reaktive.value.now
import xenakis.impl.*
import xenakis.model.Settings
import xenakis.model.flow.AudioFlowGraph
import xenakis.model.flow.ScoreObjectInfo
import xenakis.model.player.ScoreEventCollector.Event
import xenakis.model.score.*
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.misc.PlayHead

class ScorePlayer(
    private val rootScore: Score,
    override val playHead: PlayHead,
    override val client: SuperColliderClient,
    private val graph: AudioFlowGraph,
    private val settings: Settings,
    private val events: ScoreEventCollector,
    private val recorder: Recorder,
) : AbstractPlayer(DELTA_T, settings.lookAhead) {
    private val activeObjectManager = ActiveObjectManager()

    private var lastPlayFrom: Decimal = PlayHead.START

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
        recorder.startingPlayback()
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

    fun stopPlayBackInstantly(obj: ScoreObject, pos: ObjectPosition, name: String) {
        val suffix = activeObjectManager.remove(obj, pos) ?: return
        when (obj) {
            is SynthObject -> {
                graph.remove(obj, pos, suffix)
            }

            is TaskObject -> client.run("~tasks['$name'].free;")

            else -> {}
        }
    }

    override fun scheduleEvents(t: Decimal, delta: Decimal) {
        for (ev in events.eventsAt(t - delta, delta * 5)) {
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
                    val suffix = activeObjectManager.remove(obj, startPos) ?: continue
                    stopObject(obj, startPos, suffix, stopPrematurely = false)
                }

                else -> {}
            }
            ev.scheduled = true
        }
    }

    private fun stopObject(obj: ScoreObject, startPos: ObjectPosition, suffix: Int, stopPrematurely: Boolean) {
        when (obj) {
            is SynthObject -> {
                try {
                    graph.remove(obj, startPos, suffix)
                } catch (e: Exception) {
                    Logger.error("Failed to remove $obj from audio flow graph", e, Logger.Category.Playback)
                }
                val name = obj.superColliderName(suffix)
                if (stopPrematurely) {
                    client.run("if ($name != nil && $name.isRunning) { $name.free; }")
                }
            }

            is ProcessObject, is TaskObject -> {
                val name = obj.superColliderName(suffix)
                if (stopPrematurely) {
                    client.run("$name.stop;")
                }
            }

            else -> {}
        }
    }

    private fun scheduleObject(obj: ScoreObject, absolutePosition: ObjectPosition, cutoff: Decimal) {
        try {
            if (!obj.validate()) return
        } catch (e: Exception) {
            Logger.error("Failed to validate $obj", e, Logger.Category.Playback)
            return
        }
        val time = absolutePosition.time - lastPlayFrom
        val timeForExecution = (time + settings.scLangLatency.now).toString()
        val suffix = try {
            activeObjectManager.insert(obj, absolutePosition)
        } catch (e: Exception) {
            Logger.error("Failed to insert $obj into active object manager", e, Logger.Category.Playback)
            return
        }
        var info = ScoreObjectInfo(absolutePosition, suffix, null, cutoff.takeIf { it > zero } ?: zero)
        if (obj is SynthObject) {
            val placement = try {
                graph.insert(obj, absolutePosition, suffix)
            } catch (e: Exception) {
                Logger.error("Failed to insert $obj into audio flow graph", e, Logger.Category.Playback)
                return
            }
            info = info.copy(placement = placement)
        }
        val code = try {
            obj.writeCode(info)
        } catch (e: Exception) {
            Logger.error("Failed to write code for $obj", e, Logger.Category.Playback)
        }
        if (code == "") return
        try {
            client.send("schedule", listOf(timeForExecution, code))
        } catch (e: Exception) {
            Logger.error("Failed to schedule $obj", e, Logger.Category.Playback)
        }
        Logger.fine("unique name for $obj at $time: ${info.uniqueName(obj)}", Logger.Category.Playback)
        Logger.fine("time for execution: ${timeForExecution}s", Logger.Category.Playback)

    }

    override fun pausePlayback() {
        Logger.info("Pausing playback", Logger.Category.Playback)
        recorder.pausingPlayback()
        activeObjectManager.forEach { obj, startPos, suffix ->
            stopObject(obj, startPos, suffix, stopPrematurely = true)
        }
        graph.clear()
        activeObjectManager.clear()
        events.resetEvents()
        client.send("pause_play")
    }

    override fun resetPlayback() {
        if (recorder.isActive.now) recorder.stopRecording()
        client.run("s.freeAll")
    }

    companion object {
        private val DELTA_T = 0.03.toDecimal()
    }
}