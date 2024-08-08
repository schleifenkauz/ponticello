package xenakis.ui

import com.illposed.osc.OSCPacketDispatcher.DaemonThreadFactory
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.scene.shape.Line
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.model.*
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ScorePlayer(
    private val scoreView: ScoreView,
    private val project: XenakisProject,
    private val client: SuperColliderClient
) : Thread() {
    private var latencyReached = false

    private val executor = Executors.newSingleThreadScheduledExecutor(DaemonThreadFactory())

    var playHeadPosition = 0.0
        private set

    @Suppress("InconsistentCommentForJavaParameter")
    val playHead = Line(
        /* startX = */ PLAY_HEAD_WIDTH,
        /* startY = */ 20.0,
        /* endX = */ PLAY_HEAD_WIDTH,
        /* endY = */ scoreView.height - 20.0
    ).styleClass("play-head")

    init {
        isDaemon = true
        start()
        setupPlayHead()
    }

    private fun setupPlayHead() {
        playHead.viewOrder = -500.0
        playHead.strokeWidthProperty().bind(Bindings.divide(PLAY_HEAD_WIDTH, scoreView.scaleXProperty()))
        playHead.endYProperty().bind(scoreView.heightProperty().subtract(20.0))
    }

    fun setPlayHeadX(x: Double) {
        if (isPlaying) return
        playHead.layoutX = x
        playHeadPosition = scoreView.getTime(playHead.layoutX)
    }

    var isPlaying = false
        private set

    override fun run() {
        var lastTime = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            if (isPlaying && latencyReached) {
                val dt = now - lastTime
                playHeadPosition += dt / 1000.0
                val x = scoreView.getX(playHeadPosition)
                Platform.runLater {
                    if (isPlaying && latencyReached) playHead.layoutX = x
                }
            }
            lastTime = now
            try {
                sleep(10)
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
                return
            }
        }
    }

    private fun getSuffixFor(obj: ScoreObject, suffixes: MutableMap<String, Int>): String {
        val idx = suffixes.getOrPut(obj.name.now) { 0 }
        suffixes[obj.name.now] = idx + 1
        return if (idx == 0) "" else idx.toString()
    }

    private fun prepareScore(startFrom: Double): MutableList<LocatedScoreObject> {
        val unvisitedSubScores: Queue<SubScore> = LinkedList()
        unvisitedSubScores.offer(SubScore(project.score, prefix = "", ObjectPosition(-startFrom, 0.0)))
        val locatedObjects = mutableListOf<LocatedScoreObject>()
        val suffixes = mutableMapOf<String, Int>()
        while (unvisitedSubScores.isNotEmpty()) {
            val (score, prefix, position) = unvisitedSubScores.poll()
            for (obj in score.objects) {
                if (obj.muted) continue
                val absolutePosition = position + obj.position
                val t = absolutePosition.time
                if (t + obj.duration <= 0) continue
                val original = if (obj is ClonedObject) obj.original else obj
                val name = prefix + obj.name.now + getSuffixFor(obj, suffixes)
                if (original is ScoreObjectGroup) {
                    unvisitedSubScores.offer(SubScore(original.score, prefix = "${name}_", absolutePosition))
                } else {
                    locatedObjects.add(LocatedScoreObject(original, name, absolutePosition))
                }
            }
        }
        locatedObjects.sortBy { (_, _, pos) -> pos.time }
        return locatedObjects
    }

    private fun scheduleObjects(locatedObjects: MutableList<LocatedScoreObject>) {
        val env = ScorePlayEnv()
        for (located in locatedObjects) {
            val (obj, name, pos) = located
            env.advanceToTime(pos.time)
            val cutoff = (-pos.time).coerceAtLeast(0.0)
            val code = obj.writeCode(env, name, cutoff)
            env.markObjectStart(located)
            if (code == "") continue
            val t = pos.time.coerceAtLeast(0.0)
            var delay = (t * 1000).toLong()
            //schedule a good amount of time before the actual event, but leave a bit of latency for the early events
            delay = (delay - LOOK_AHEAD).coerceAtLeast(10)
            executor.schedule({
                println("SCHEDULE $name for $t + $LATENCY")
                if (isPlaying) client.send("schedule", listOf((t + LATENCY).toString(), code))
            }, delay, TimeUnit.MILLISECONDS)
        }
    }

    fun play() {
        if (isPlaying) return
        val startFrom = scoreView.getTime(playHead.layoutX - PLAY_HEAD_WIDTH)
        thread(isDaemon = true) {
            client.send("start_play")
            val locatedObjects = prepareScore(startFrom)
            scheduleObjects(locatedObjects)
            latencyReached = false
            isPlaying = true
            sleep((LATENCY * 1000).toLong())
            latencyReached = true
        }
    }

    fun pause() {
        isPlaying = false
        client.send("pause_play")
    }

    fun reset() {
        pause()
        client.run("s.freeAll")
    }

    fun movePlayHead(pos: Double) {
        if (isPlaying) return
        playHeadPosition = pos
        Platform.runLater { playHead.layoutX = scoreView.getX(pos) }
    }

    fun repaint() {
        scoreView.children.add(playHead)
        playHead.layoutX = scoreView.getX(playHeadPosition)
    }

    fun close() {
        executor.shutdownNow()
    }

    private data class SubScore(val score: Score, val prefix: String, val position: ObjectPosition)

    data class LocatedScoreObject(
        val obj: ScoreObject,
        val name: String,
        val absolutePosition: ObjectPosition,
    )

    companion object {
        private const val LATENCY = 0.3
        private const val LOOK_AHEAD = 1000
        private const val PLAY_HEAD_WIDTH = 2.0
    }
}