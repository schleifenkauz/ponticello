package xenakis.ui

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.scene.shape.Line

class PlayHead(private val scoreView: ScoreView) {
    private var playHeadPosition = 0.0

    private val playHead = Line(PLAY_HEAD_WIDTH, 20.0, PLAY_HEAD_WIDTH, scoreView.height - 20.0).styleClass("play-head")

    val currentTime get() = scoreView.getTime(playHead.layoutX - PLAY_HEAD_WIDTH)

    init {
        setupPlayHead()
    }

    private fun setupPlayHead() {
        playHead.viewOrder = -500.0
        playHead.strokeWidthProperty().bind(Bindings.divide(PLAY_HEAD_WIDTH, scoreView.scaleXProperty()))
        playHead.endYProperty().bind(scoreView.heightProperty().subtract(20.0))
    }

    fun setPlayHeadX(x: Double) {
        playHead.layoutX = x
        playHeadPosition = scoreView.getTime(playHead.layoutX)
    }

    fun repaint() {
        scoreView.children.add(playHead)
        playHead.layoutX = scoreView.getX(playHeadPosition)
    }

    fun movePlayHead(pos: Double) {
        playHeadPosition = pos
        Platform.runLater { playHead.layoutX = scoreView.getX(pos) }
    }

    fun advance(dt: Double) {
        movePlayHead(playHeadPosition + dt)
    }

    companion object {
        private const val PLAY_HEAD_WIDTH = 2.0
    }
}