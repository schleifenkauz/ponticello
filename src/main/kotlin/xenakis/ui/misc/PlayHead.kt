package xenakis.ui.misc

import fxutils.styleClass
import javafx.application.Platform
import javafx.scene.shape.Line
import xenakis.impl.Decimal
import xenakis.impl.withPrecision
import xenakis.impl.zero
import xenakis.model.score.ObjectPosition
import xenakis.ui.score.ScorePane
import xenakis.ui.score.TimeCodeView

class PlayHead(private val pane: ScorePane) {
    var currentTime = START
        private set(value) {
            field = value
            Platform.runLater {
                pane.context[TimeCodeView].displayTime(value)
            }
        }

    private val playHead = Line().styleClass("play-head")

    init {
        playHead.viewOrder = -500.0
        playHead.strokeWidth = PLAY_HEAD_WIDTH
        playHead.isMouseTransparent = true
        playHead.endYProperty().bind(pane.heightProperty())
        playHead.startY = 0.0
    }

    fun setPlayHeadX(x: Double) {
        playHead.layoutX = x
        currentTime = pane.getTime(x).withPrecision(3)
    }

    fun movePlayHead(pos: Decimal) {
        currentTime = pos
        Platform.runLater { updatePosition() }
    }

    fun movePlayHeadToStart() {
        movePlayHead(START)
    }

    fun updatePosition() {
        if (!pane.children.contains(playHead)) pane.children.add(playHead)
        playHead.layoutX = pane.getX(currentTime)
    }

    fun advance(dt: Decimal) {
        movePlayHead(currentTime + dt)
    }

    companion object {
        const val PLAY_HEAD_WIDTH = 2.0
        val START = zero(ObjectPosition.TIME_PRECISION)
    }
}