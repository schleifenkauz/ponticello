package xenakis.model

import javafx.geometry.HorizontalDirection
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.model.ScoreEventCollector.Event
import xenakis.ui.PlayHead

class ScorePlayer(
    private val rootScore: Score,
    override val playHead: PlayHead,
    override val client: SuperColliderClient,
    private val env: ScorePlayEnv = ScorePlayEnv(rootScore.context[Settings]),
    private val events: ScoreEventCollector,
    private val recorder: Recorder,
) : AbstractPlayer(DELTA_T) {

    private var lastPlayFrom: Double = 0.0

    override val lookAhead: Double
        get() = env.lookAhead

    private fun activeObjects(time: Double, delta: Double): List<Event> {
        val dest = mutableListOf<Event>()
        collectActiveObjects(ObjectPosition(0.0, 0.0), rootScore, time, delta, dest)
        return dest
    }

    private fun collectActiveObjects(
        position: ObjectPosition, score: Score, time: Double, delta: Double,
        dest: MutableList<Event>
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

    override fun startPlay(startFrom: Double): Boolean {
        client.sendAsync("start_play")
        recorder.startingPlayback()
        Logger.fine("Starting playback at $startFrom", Logger.Category.Playback)
        lastPlayFrom = startFrom
        val activeObjects = activeObjects(startFrom, delta = env.lookAhead)
        for ((_, position, inst) in activeObjects) {
            if (!inst.muted) {
                scheduleInstantly(inst, position)
            }
        }
        return true
    }

    fun scheduleInstantly(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        val delta = position.time - playHead.currentTime
        val pos = ObjectPosition(playHead.currentTime + delta.coerceAtLeast(0.0), position.y)
        val name = env.markStart(inst, position)
        Logger.fine("Scheduling $obj at $pos, delta: $delta", Logger.Category.Playback)
        scheduleObject(obj, pos, name, cutoff = -delta.coerceAtMost(0.0))
    }

    fun stopPlayBackInstantly(activeInstance: ScoreObjectInstance, pos: ObjectPosition, name: String) {
        env.markEnd(activeInstance, pos)
        when (activeInstance.obj) {
            is SynthObject -> client.run("~synths['$name'].free;")
            is TaskObject -> client.run("~tasks['$name'].free;")
            else -> {}
        }
    }

    override fun scheduleEvents(t: Double, delta: Double) {
        for (ev in events.eventsAt(t - delta, delta * 5)) {
            val (type, position, inst) = ev
            if (inst.muted) continue
            val obj = inst.obj
            when (type) {
                Event.Type.ObjectStart -> {
                    Logger.fine("ObjectStart: $obj at $position", Logger.Category.Playback)
                    val name = env.markStart(inst, position)
                    scheduleObject(obj, position, name, cutoff = 0.0)
                }

                Event.Type.ObjectEnd -> {
                    Logger.fine("ObjectEnd: $obj at $position", Logger.Category.Playback)
                    env.markEnd(inst, position + ObjectPosition(-obj.duration, 0.0))
                }

                else -> {}
            }
            ev.scheduled = true
        }
    }

    private fun scheduleObject(obj: ScoreObject, absolutePosition: ObjectPosition, name: String, cutoff: Double) {
        val time = absolutePosition.time - lastPlayFrom
        val timeForExecution = (time + env.scLangLatency).toString()
        val o = if (cutoff > 0.0) obj.cut(cutoff, HorizontalDirection.RIGHT, obj.name.now) ?: obj else obj
        val code = o.writeCode(name, absolutePosition, env)
        if (code == "") return
        Logger.fine("unique name for $o at $time: $name", Logger.Category.Playback)
        Logger.fine("time for execution: ${timeForExecution}s", Logger.Category.Playback)
        client.send("schedule", listOf(timeForExecution, code))
    }

    override fun pausePlayback() {
        Logger.info("Pausing playback", Logger.Category.Playback)
        recorder.pausingPlayback()
        client.send("pause_play")
        events.resetEvents()
        env.clear()
    }

    override fun resetPlayback() {
        if (recorder.isActive) recorder.stopRecording()
        client.run("s.freeAll")
    }

    companion object {
        private const val DELTA_T = 0.03
    }
}