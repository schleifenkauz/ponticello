package xenakis.ui

import javafx.geometry.HorizontalDirection
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.model.*
import java.util.*
import java.util.logging.Logger
import kotlin.concurrent.thread

class ScorePlayer(
    private val rootScore: Score,
    private val playHead: PlayHead,
    private val client: SuperColliderClient
) : Thread(), ScoreListener {
    private val events = TreeSet<Event>()
    private val scoreInstances = mutableMapOf<Score, MutableSet<ScoreObjectInstance>>()
    private fun scoreInstances(score: Score) = scoreInstances.getOrPut(score) { mutableSetOf() }

    private val env = ScorePlayEnv(rootScore.context)

    var isPlaying = false
        private set
    private var lastPlayFrom: Double = 0.0

    init {
        rootScore.addListener(this)
        isDaemon = true
        start()
    }

    private fun absolutePositions(score: Score): List<ObjectPosition> = when {
        score == rootScore -> listOf(ObjectPosition(0.0, 0.0))
        else -> scoreInstances(score).flatMap { inst ->
            absolutePositions(inst.score).map { pos -> pos + inst.position }
        }
    }

    override fun addedObject(score: Score, inst: ScoreObjectInstance) = synchronized(events) {
        if (inst.muted) return
        addToPlayback(inst, score)
    }

    override fun removedObject(score: Score, inst: ScoreObjectInstance): Unit = synchronized(events) {
        if (inst.muted) return
        removeFromPlayback(inst)
    }

    private fun addToPlayback(inst: ScoreObjectInstance, score: Score) {
        val obj = inst.obj
        if (obj is ScoreObjectGroup) {
            scoreInstances(obj.score).add(inst)
            obj.score.addListener(this)
        } else {
            logger.info("Added $inst to ${score.scoreName.now}")
            for (pos in absolutePositions(score)) {
                val posStart = pos + inst.position
                val posEnd = posStart + ObjectPosition(obj.duration, 0.0)
                logger.info("   at $posStart")
                if (isPlaying && playHead.currentTime in posStart.time - env.lookAhead..posEnd.time) {
                    scheduleInstantly(inst, posStart, playHead.currentTime)
                }
                events.add(Event(Event.Type.ObjectStart, posStart, inst))
                events.add(Event(Event.Type.ObjectEnd, posStart + posEnd, inst))
            }
        }
    }

    private fun removeFromPlayback(inst: ScoreObjectInstance) {
        val obj = inst.obj
        val itr = events.iterator()
        for (ev in itr) {
            if (ev.obj == inst) {
                itr.remove()
                logger.info("Removed ${ev.type}: ${ev.obj} at ${ev.absolutePosition}")
            }
        }
        if (obj is ScoreObjectGroup) {
            scoreInstances(obj.score).remove(inst)
            for (subInst in obj.score.objectInstances) {
                removedObject(obj.score, subInst)
            }
            obj.score.removeListener(this)
        } else if (isPlaying) {
            for ((activeInstance, pos, name) in env.activeInstances(inst)) {
                val activeObj = activeInstance.obj
                if (activeObj is SynthObject) {
                    client.run("~synths['$name'].free;")
                } else if (activeObj is TaskObject) {
                    client.run("~tasks['$name'].free;")
                }
                env.markEnd(activeInstance, pos)
                val endPos = pos + ObjectPosition(activeObj.duration, 0.0)
                events.remove(Event(Event.Type.ObjectEnd, endPos, activeInstance))
            }
        }
    }

    override fun movedObject(score: Score, inst: ScoreObjectInstance) =
        synchronized(events) {
            removeFromPlayback(inst)
            addToPlayback(inst, score)
        }

    override fun toggledMute(score: Score, inst: ScoreObjectInstance, muted: Boolean) = synchronized(events) {
        if (muted) removeFromPlayback(inst)
        else addToPlayback(inst, score)
    }

    private fun eventsAt(time: Double, delta: Double): List<Event> = synchronized(events) {
        val dummy = Event(Event.Type.Dummy, ObjectPosition(time, 0.0), null)
        return events.tailSet(dummy).takeWhile { ev -> ev.absolutePosition.time < time + delta }
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
                collectActiveObjects(absolutePosition, score, time, delta, dest)
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
        logger.info("Starting playback at $startFrom")
        lastPlayFrom = startFrom
        val activeObjects = activeObjects(startFrom, delta = env.lookAhead)
        for ((_, position, inst) in activeObjects) {
            if (inst != null && !inst.muted) {
                scheduleInstantly(inst, position, lastPlayFrom)
            }
        }
    }

    private fun scheduleInstantly(inst: ScoreObjectInstance, position: ObjectPosition, tNow: Double) {
        val obj = inst.obj
        val delta = inst.start - tNow
        val pos = ObjectPosition(tNow + delta.coerceAtLeast(0.0), position.y)
        val name = env.markStart(inst, position) //important that we mark the original object not the cutoff one
        logger.info("   Scheduling $obj at $pos, delta: $delta")
        scheduleObject(obj, pos, name, cutoff = -delta.coerceAtMost(0.0))
    }

    private fun scheduleEvents(t: Double, delta: Double) {
        var printedInterval = false
        for ((type, position, inst) in eventsAt(t, delta)) {
            if (inst == null || inst.muted) continue
            if (!printedInterval) {
                logger.info("interval at t=${t}s, delta = ${delta}s")
                printedInterval = true
            }
            val obj = inst.obj
            when (type) {
                Event.Type.ObjectStart -> {
                    logger.info("   ObjectStart: $obj at $position")
                    val name = env.markStart(inst, position)
                    scheduleObject(obj, position, name, cutoff = 0.0)
                }

                Event.Type.ObjectEnd -> {
                    logger.info("   ObjectEnd: $obj at $position")
                    env.markEnd(inst, position)
                }

                else -> {}
            }
        }
    }

    private fun scheduleObject(obj: ScoreObject, absolutePosition: ObjectPosition, name: String, cutoff: Double) {
        val time = absolutePosition.time - lastPlayFrom
        val timeForExecution = (time + env.scLangLatency).toString()
        val o = if (cutoff > 0.0) obj.cut(cutoff, HorizontalDirection.RIGHT, obj.name.now) ?: obj else obj
        val code = o.writeCode(name, absolutePosition, env)
        if (code == "") return
        logger.info("   unique name for $o at $time: $name")
        logger.info("   time for execution: ${timeForExecution}s")
        client.send("schedule", listOf(timeForExecution, code))
    }

    fun play() {
        if (isPlaying) return
        thread(isDaemon = true) {
            logger.info("START PLAYBACK")
            client.send("start_play")
            startPlay(playHead.currentTime)
            sleep(toMs(env.lookAhead))
            isPlaying = true
        }
    }

    fun pause() {
        if (!isPlaying) return
        isPlaying = false
        logger.info("PAUSE PLAYBACK")
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

    data class Event(
        val type: Type,
        val absolutePosition: ObjectPosition,
        val obj: ScoreObjectInstance?
    ) : Comparable<Event> {
        override fun compareTo(other: Event): Int = compareValuesBy(this, other, Event::absolutePosition)

        enum class Type {
            Dummy, ObjectStart, ObjectEnd;
        }
    }

    companion object {
        private const val DELTA_T = 0.03

        private val logger = Logger.getLogger("ScorePlayer")
    }
}