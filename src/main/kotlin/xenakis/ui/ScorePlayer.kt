package xenakis.ui

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.scene.shape.Line
import xenakis.impl.UDPSuperColliderClient
import xenakis.model.XenakisProject

class ScorePlayer(
    private val scoreView: ScoreView,
    private val project: XenakisProject,
    private val client: UDPSuperColliderClient
) : Thread() {
    var isRecording = false

    var playHeadPosition = 0.0
        private set

    val playHead = Line(
        /* startX = */ PLAY_HEAD_WIDTH,
        /* startY = */ 20.0,
        /* endX = */ PLAY_HEAD_WIDTH,
        /* endY = */ scoreView.height - 20.0
    ).styleClass("play-head")

    init {
        isDaemon = true
        start()
        setupIndicator()
    }

    private fun setupIndicator() {
        playHead.strokeWidthProperty().bind(Bindings.divide(PLAY_HEAD_WIDTH, scoreView.scaleXProperty()))
        playHead.setupDragging { _, old, dx, _ ->
            if (!isPlaying) {
                playHead.layoutX = (old.minX + dx).coerceIn(PLAY_HEAD_WIDTH, scoreView.width - PLAY_HEAD_WIDTH)
                playHeadPosition = scoreView.getTime(playHead.layoutX)
            }
        }
        playHead.setOnMouseClicked {
            playHead.toFront()
        }
        playHead.endYProperty().bind(scoreView.heightProperty().subtract(20.0))
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
        project.playScore(startTime)
        isPlaying = true
    }

    fun pause() {
        isPlaying = false
        client.postAsync("s.freeAll;")
        client.postAsync("~player_task.stop;")
    }

    fun reset() {
        Platform.runLater { playHead.layoutX = PLAY_HEAD_WIDTH }
        playHeadPosition = 0.0
    }

    fun toggleRecording() {
        if (isRecording) {
            client.postAsync("s.record")
        } else {
            client.postAsync("s.stopRecording")
        }
        isRecording = !isRecording
    }

    fun repaint() {
        scoreView.children.add(playHead)
        playHead.layoutX = scoreView.getX(playHeadPosition)
    }

    companion object {
        private const val PLAY_HEAD_WIDTH = 2.0
    }
}