package xenakis.ui

import javafx.geometry.HorizontalDirection
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.model.*
import java.util.*
import kotlin.concurrent.thread

class ScorePlayer(
    private val rootScore: Score,
    private val playHead: PlayHead,
    private val client: SuperColliderClient
) : Thread(), ScoreListener {
    //TODO synchronize between player thread and score changes from FXThread
    private val events = TreeSet<Event>()
    private val scoreInstances = mutableMapOf<Score, MutableSet<ScoreObjectInstance>>()
    private fun scoreInstances(score: Score) = scoreInstances.getOrPut(score) { mutableSetOf() }

    private val env = ScorePlayEnv()

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

    override fun addedObject(score: Score, inst: ScoreObjectInstance) {
        val obj = inst.obj
        if (obj is ScoreObjectGroup) {
            scoreInstances(obj.score).add(inst)
            obj.score.addListener(this)
        }
        for (pos in absolutePositions(score)) {
            val position = pos + inst.position
            events.add(Event(Event.Type.ObjectStart, position, inst))
            events.add(Event(Event.Type.ObjectEnd, position + ObjectPosition(obj.duration, 0.0), inst))
        }
    }

    override fun removedObject(score: Score, inst: ScoreObjectInstance) {
        val obj = inst.obj
        for (pos in absolutePositions(score)) {
            val position = pos + inst.position
            events.add(Event(Event.Type.ObjectStart, position, inst))
            events.add(Event(Event.Type.ObjectEnd, position + ObjectPosition(obj.duration, 0.0), inst))
        }
        if (obj is ScoreObjectGroup) {
            scoreInstances(score).remove(inst)
            for (subInst in obj.score.objectInstances) {
                removedObject(obj.score, inst)
            }
            obj.score.removeListener(this)
        }
    }

    //TODO there is a bug here
    override fun movedObject(score: Score, inst: ScoreObjectInstance, oldPosition: ObjectPosition) {
        val oldInstance = ScoreObjectInstance(inst.obj.createReference(), oldPosition.time, oldPosition.y, inst.muted)
        removedObject(score, oldInstance)
        addedObject(score, inst)
    }

    private fun eventsAt(time: Double, delta: Double): List<Event> {
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
                scheduleEvents(playHead.currentTime + LOOK_AHEAD, dt)
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
        println("Starting playback at $startFrom")
        lastPlayFrom = startFrom
        val activeObjects = activeObjects(startFrom, delta = LOOK_AHEAD)
        for ((_, position, inst) in activeObjects) {
            if (inst != null && !inst.muted) {
                var obj = inst.obj
                val delta = inst.start - startFrom
                if (delta < 0.0) obj = obj.cut(-delta, HorizontalDirection.RIGHT, obj.name.now) ?: obj
                val pos = position + ObjectPosition(delta.coerceAtLeast(0.0), 0.0)
                env.markStart(obj, pos)
                println("Scheduling $obj at $pos, delta: $delta")
                scheduleObject(obj, pos)
            }
        }
    }

    private fun scheduleEvents(t: Double, delta: Double) {
        for ((type, position, inst) in eventsAt(t, delta)) {
            if (inst == null || inst.muted) continue
            println("interval at t=${t}s, delta = ${delta}s")
            val obj = inst.obj
            when (type) {
                Event.Type.ObjectStart -> {
                    println("ObjectStart: $obj at $position")
                    env.markStart(obj, position)
                    scheduleObject(obj, position)
                }

                Event.Type.ObjectEnd -> {
                    println("ObjectEnd: $obj at $position")
                    env.markEnd(obj, position)
                }

                else -> {}
            }
        }
    }

    private fun scheduleObject(obj: ScoreObject, absolutePosition: ObjectPosition) {
        val time = absolutePosition.time - lastPlayFrom
        val name = env.getUniqueNameFor(obj)
        val timeForExecution = (time + SC_LANG_LATENCY).toString()
        val code = obj.writeCode(name, absolutePosition, env)
        if (code == "") return
        println("unique name for $obj at $time: $name")
        println("time for execution: ${timeForExecution}s")
        println("code: $code")
        client.send("schedule", listOf(timeForExecution, code))
    }

    fun play() {
        if (isPlaying) return
        val startFrom = playHead.currentTime
        thread(isDaemon = true) {
            println("START PLAYBACK")
            client.send("start_play")
            startPlay(startFrom)
            sleep(toMs(LOOK_AHEAD))
            isPlaying = true
        }
    }

    fun pause() {
        if (!isPlaying) return
        isPlaying = false
        println("PAUSE PLAYBACK")
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
        const val SERVER_LATENCY: Double = 0.3
        private const val SC_LANG_LATENCY = 1.0
        private const val LOOK_AHEAD = SC_LANG_LATENCY + SERVER_LATENCY
    }
}