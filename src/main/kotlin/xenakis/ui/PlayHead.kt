package xenakis.ui

import javafx.application.Platform
import javafx.scene.layout.Pane
import javafx.scene.shape.Line

class PlayHead {
    var currentTime = 0.0
        private set

    private val playHead = Line().styleClass("play-head")

    private lateinit var timeBlock: TimeBlock
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
        movePlayHead(0.0)
        timeBlock = target
        pane = target
    }

    fun setPlayHeadX(x: Double) {
        playHead.layoutX = x
        currentTime = timeBlock.getTime(x)
    }

    fun movePlayHead(pos: Double) {
        currentTime = pos
        Platform.runLater { updatePosition() }
    }

    fun updatePosition() {
        if (!pane.children.contains(playHead)) pane.children.add(playHead)
        playHead.layoutX = timeBlock.getX(currentTime)
    }

    fun advance(dt: Double) {
        movePlayHead(currentTime + dt)
    }

    companion object {
        const val PLAY_HEAD_WIDTH = 2.0
    }
}