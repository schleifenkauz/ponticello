package xenakis.ui.misc

import fxutils.styleClass
import javafx.application.Platform
import javafx.scene.layout.Pane
import javafx.scene.shape.Line
import xenakis.impl.Decimal
import xenakis.impl.zero
import xenakis.model.score.ObjectPosition
import xenakis.ui.score.TimeBlock

class PlayHead {
    var currentTime = START
        private set

    private val playHead = Line().styleClass("play-head")

    lateinit var timeBlock: TimeBlock
        private set
    lateinit var pane: Pane
        private set

    init {
        playHead.viewOrder = -500.0
        playHead.strokeWidth = PLAY_HEAD_WIDTH
    }

    fun <T> attachTo(target: T, verticalPadding: Double) where T : TimeBlock, T : Pane {
        (playHead.parent as? Pane)?.children?.remove(playHead)
        playHead.startY = verticalPadding
        if (playHead.endYProperty().isBound) playHead.endYProperty().unbind()
        playHead.endYProperty().bind(target.heightProperty().subtract(verticalPadding))
        movePlayHead(START)
        timeBlock = target
        pane = target
    }

    fun setPlayHeadX(x: Double) {
        playHead.layoutX = x
        currentTime = timeBlock.getTime(x)
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
        playHead.layoutX = timeBlock.getX(currentTime)
    }

    fun advance(dt: Decimal) {
        movePlayHead(currentTime + dt)
    }

    companion object {
        const val PLAY_HEAD_WIDTH = 2.0
        val START = zero(ObjectPosition.TIME_PRECISION)
    }
}