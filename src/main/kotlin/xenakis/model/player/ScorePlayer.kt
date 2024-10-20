package xenakis.model.player

import javafx.geometry.HorizontalDirection
import reaktive.value.now
import xenakis.impl.*
import xenakis.model.Logger
import xenakis.model.player.ScoreEventCollector.Event
import xenakis.model.score.*
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.misc.PlayHead

class ScorePlayer(
    private val rootScore: Score,
    override val playHead: PlayHead,
    override val client: SuperColliderClient,
    private val env: ScorePlayEnv,
    private val events: ScoreEventCollector,
    private val recorder: Recorder,
) : AbstractPlayer(DELTA_T) {

    private var lastPlayFrom: Decimal = PlayHead.START

    override val lookAhead: Decimal
        get() = env.lookAhead

    private fun activeObjects(time: Decimal, delta: Decimal): List<Event> {
        val dest = mutableListOf<Event>()
        collectActiveObjects(ObjectPosition(0.0, 0.0), rootScore, time, delta, dest)
        return dest
    }

    private fun collectActiveObjects(
        position: ObjectPosition, score: Score, time: Decimal, delta: Decimal,
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

    override fun startPlay(startFrom: Decimal): Boolean {
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
        val pos = ObjectPosition(playHead.currentTime + delta.coerceAtLeast(zero), position.y)
        val name = env.markStart(inst, position)
        Logger.fine("Scheduling $obj at $pos, delta: $delta", Logger.Category.Playback)
        scheduleObject(obj, pos, name, cutoff = -delta.coerceAtMost(zero))
    }

    fun stopPlayBackInstantly(activeInstance: ScoreObjectInstance, pos: ObjectPosition, name: String) {
        env.markEnd(activeInstance, pos)
        when (activeInstance.obj) {
            is SynthObject -> client.run("~synths['$name'].free;")
            is TaskObject -> client.run("~tasks['$name'].free;")
            else -> {}
        }
    }

    override fun scheduleEvents(t: Decimal, delta: Decimal) {
        for (ev in events.eventsAt(t - delta, delta * 5)) {
            val (type, position, inst) = ev
            if (inst.muted) continue
            val obj = inst.obj
            when (type) {
                Event.Type.ObjectStart -> {
                    Logger.fine("ObjectStart: $obj at $position", Logger.Category.Playback)
                    val name = env.markStart(inst, position)
                    scheduleObject(obj, position, name, cutoff = zero)
                }

                Event.Type.ObjectEnd -> {
                    Logger.fine("ObjectEnd: $obj at $position", Logger.Category.Playback)
                    env.markEnd(inst, position + ObjectPosition(-obj.duration, zero))
                }

                else -> {}
            }
            ev.scheduled = true
        }
    }

    private fun scheduleObject(obj: ScoreObject, absolutePosition: ObjectPosition, name: String, cutoff: Decimal) {
        val time = absolutePosition.time - lastPlayFrom
        val timeForExecution = (time + env.scLangLatency).toString()
        val o = if (cutoff > zero) obj.cut(cutoff, HorizontalDirection.RIGHT, obj.name.now) ?: obj else obj
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
        private val DELTA_T = 0.03.toDecimal()
    }
}