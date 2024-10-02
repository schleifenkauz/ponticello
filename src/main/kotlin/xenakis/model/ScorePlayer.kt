package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import javafx.geometry.HorizontalDirection
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.model.ScoreEventCollector.Event
import xenakis.ui.PlayHead
import kotlin.concurrent.thread

class ScorePlayer(
    private val rootScore: Score,
    private val playHead: PlayHead,
    private val client: SuperColliderClient
) : Thread() {
    val env = ScorePlayEnv(rootScore.context[Settings])
    private val events = ScoreEventCollector(rootScore, this, env)

    var isPlaying = false
        private set
    private var lastPlayFrom: Double = 0.0
    val currentTime get() = playHead.currentTime

    init {
        rootScore.context[ScorePlayer] = this
        isDaemon = true
        start()
    }

    private fun activeObjects(time: Double, delta: Double): List<Event> {
        val dest = mutableListOf<Event>()
        collectActiveObjects(ObjectPosition(0.0, 0.0), rootScore, time, delta, dest)
        return dest
    }

    private fun collectActiveObjects(
        position: ObjectPosition, score: Score, time: Double, delta: Double,
        dest: MutableList<Event>
    ) {
        if (position.time > time) return
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

    override fun run() {
        var lastTime = System.currentTimeMillis()
        while (!interrupted()) {
            val now = System.currentTimeMillis()
            if (isPlaying) {
                val dt = (now - lastTime) / 1000.0
                scheduleEvents(playHead.currentTime + env.lookAhead, dt)
                playHead.advance(dt)
            }
            lastTime = now
            try {
                sleep(toMs(DELTA_T))
            } catch (ex: InterruptedException) {
                //ex.printStackTrace()
                return
            }
        }
    }

    private fun toMs(t: Double) = (t * 1000).toLong()

    private fun startPlay(startFrom: Double) {
        Logger.fine("Starting playback at $startFrom", Logger.Category.Playback)
        lastPlayFrom = startFrom
        val activeObjects = activeObjects(startFrom, delta = env.lookAhead)
        for ((_, position, inst) in activeObjects) {
            if (inst != null && !inst.muted) {
                scheduleInstantly(inst, position)
            }
        }
    }

    fun scheduleInstantly(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        val delta = position.time - currentTime
        val pos = ObjectPosition(currentTime + delta.coerceAtLeast(0.0), position.y)
        val name = env.markStart(inst, position) //important that we mark the original object not the cutoff one
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

    private fun scheduleEvents(t: Double, delta: Double) {
        var printedInterval = false
        for (ev in events.eventsAt(t, delta)) {
            val (type, position, inst) = ev
            if (inst == null || inst.muted) continue
            if (!printedInterval) {
                Logger.fine("interval at t=${t}s, delta = ${delta}s", Logger.Category.Playback)
                printedInterval = true
            }
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

    fun play() {
        if (isPlaying) return
        thread(isDaemon = true) {
            Logger.info("Starting playback", Logger.Category.Playback)
            client.send("start_play")
            startPlay(playHead.currentTime)
            sleep(toMs(env.lookAhead))
            isPlaying = true
        }
    }

    fun pause() {
        if (!isPlaying) return
        isPlaying = false
        Logger.info("Pausing playback", Logger.Category.Playback)
        events.resetEvents()
        env.clear()
        client.send("pause_play")
    }

    fun reset() {
        pause()
        client.run("s.freeAll")
    }

    fun close() {
        interrupt()
    }

    companion object : PublicProperty<ScorePlayer> by publicProperty("ScorePlayer") {
        private const val DELTA_T = 0.03
    }
}