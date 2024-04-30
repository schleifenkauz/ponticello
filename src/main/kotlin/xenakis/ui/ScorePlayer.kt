package xenakis.ui

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.scene.shape.Line
import xenakis.impl.ScWriter
import xenakis.impl.UDPSuperColliderClient
import xenakis.ui.ScoreView.Companion.PIXELS_PER_SECOND
import java.io.StringWriter

class ScorePlayer(private val scoreView: ScoreView, private val client: UDPSuperColliderClient) : Thread() {
    var isRecording = false

    private val indicator = Line(
        /* startX = */ PLAY_HEAD_WIDTH,
        /* startY = */ 1.0,
        /* endX = */ PLAY_HEAD_WIDTH,
        /* endY = */ scoreView.prefHeight - PLAY_HEAD_WIDTH
    ).styleClass("play-head")

    init {
        isDaemon = true
        start()
        setupIndicator()
    }

    private fun setupIndicator() {
        scoreView.children.add(indicator)
        indicator.strokeWidthProperty().bind(Bindings.divide(PLAY_HEAD_WIDTH, scoreView.scaleXProperty()))
        indicator.setupDragging { _, old, dx, _ ->
            if (!isPlaying) {
                val sdx = dx / scoreView.scaleX
                indicator.layoutX = (old.minX + sdx).coerceIn(PLAY_HEAD_WIDTH, scoreView.score.totalDuration * PIXELS_PER_SECOND)
            }
        }
        indicator.setOnMouseClicked {
            indicator.toFront()
        }
    }

    var isPlaying = false
        private set

    override fun run() {
        var lastTime = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            if (isPlaying) {
                val dt = now - lastTime
                val dx = PIXELS_PER_SECOND * dt / 1000
                Platform.runLater {
                    indicator.layoutX += dx
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
        val startTime = (indicator.layoutX - PLAY_HEAD_WIDTH) / PIXELS_PER_SECOND
        val writer = StringWriter()
        scoreView.score.writePlayerTask(ScWriter(writer), startTime)
        client.postAsync(writer.toString())
        isPlaying = true
    }

    fun pause() {
        isPlaying = false
        client.postAsync("s.freeAll;")
        client.postAsync("~player_task.stop;")
    }

    fun reset() {
        indicator.layoutX = PLAY_HEAD_WIDTH
    }

    fun toggleRecording() {
        if (isRecording) {
            client.postAsync("s.record")
        } else {
            client.postAsync("s.stopRecording")
        }
        isRecording = !isRecording
    }

    companion object {
        private const val PLAY_HEAD_WIDTH = 2.0
    }
}