package xenakis.ui

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.scene.shape.Line
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient
import xenakis.impl.async
import xenakis.impl.code
import xenakis.model.*
import java.util.*

class ScorePlayer(
    private val scoreView: ScoreView,
    private val project: XenakisProject,
    private val client: SuperColliderClient
) : Thread() {
    private var nthStart = 0

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
            if (isPlaying) {
                val dt = now - lastTime
                playHeadPosition += dt / 1000.0
                val x = scoreView.getX(playHeadPosition)
                Platform.runLater {
                    if (isPlaying) playHead.layoutX = x
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

    private fun writePlayerTask(writer: ScWriter, rootScore: Score, startFrom: Double) {
        val unvisitedSubScores: Queue<SubScore> = LinkedList()
        unvisitedSubScores.offer(SubScore(rootScore, prefix = "", ObjectPosition(-startFrom, 0.0)))
        val locatedObjects = mutableListOf<LocatedScoreObject>()
        val suffixes = mutableMapOf<String, Int>()
        while (unvisitedSubScores.isNotEmpty()) {
            val (score, prefix, position) = unvisitedSubScores.poll()
            for (obj in score.objects) {
                if (obj.muted) continue
                val absolutePosition = position + obj.position
                if (absolutePosition.time + obj.duration <= 0) continue
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
        val env = ScorePlayEnv(writer.writer, nthStart)
        for (located in locatedObjects) {
            val (obj, name, pos) = located
            env.advanceToTime(pos.time)
            obj.writeCode(env, name, pos.time)
            env.markObjectStart(located)
        }
    }

    fun play() {
        if (isPlaying) return
        val startTime = scoreView.getTime(playHead.layoutX - PLAY_HEAD_WIDTH)
        async(timeLimit = 2000) {
            client.eval(code {
                +"~play = $nthStart"
                //+"~startRecording.value(0)"
                writer.appendLine("~synths = ();")
                writer.appendLine("~tasks = ();")
                writePlayerTask(writer, project.score, startTime)
                nthStart += 1
                +"nil"
            }).join()
            isPlaying = true
        }
    }

    fun pause() {
        isPlaying = false
        client.run {
            +"~play = false"
            +"~tasks.do { |t| if (t.isPlaying) { t.stop; } }"
            +"~synths.do { |s| if (s.isRunning) { s.free } }"
        }
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

    private data class SubScore(val score: Score, val prefix: String, val position: ObjectPosition)

    data class LocatedScoreObject(val obj: ScoreObject, val name: String, val absolutePosition: ObjectPosition)

    companion object {
        private const val PLAY_HEAD_WIDTH = 2.0
    }
}