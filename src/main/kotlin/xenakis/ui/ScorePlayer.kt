package xenakis.ui

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.scene.shape.Line
import xenakis.impl.SuperColliderClient
import xenakis.impl.async
import xenakis.impl.code
import xenakis.model.XenakisProject

class ScorePlayer(
    private val scoreView: ScoreView,
    private val project: XenakisProject,
    private val client: SuperColliderClient
) : Thread() {
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

    fun play() {
        if (isPlaying) return
        val startTime = scoreView.getTime(playHead.layoutX - PLAY_HEAD_WIDTH)
        async(timeLimit = 2000) {
            client.eval(code {
                +"~play = true"
                //+"~startRecording.value(0)"
                project.run { playScore(startTime) }
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
            +"~synths.do { |s| s.free }"
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

    companion object {
        private const val PLAY_HEAD_WIDTH = 2.0
    }
}