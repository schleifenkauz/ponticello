package ponticello.ui.misc

import fxutils.styleClass
import javafx.application.Platform
import javafx.scene.shape.Line
import ponticello.impl.Decimal
import ponticello.impl.asTime
import ponticello.ui.score.ScorePane
import ponticello.ui.score.TimeCodeView

class PlayHead(private val pane: ScorePane) {
    var currentTime = 0.0.asTime
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

    fun movePlayHead(pos: Decimal) {
        currentTime = pos
        Platform.runLater { updatePosition() }
    }

    fun movePlayHeadToStart() {
        movePlayHead(0.0.asTime)
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
    }
}