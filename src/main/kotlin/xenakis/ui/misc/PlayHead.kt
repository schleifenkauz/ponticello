package xenakis.ui.misc

import fxutils.styleClass
import hextant.context.Context
import javafx.application.Platform
import javafx.scene.layout.Pane
import javafx.scene.shape.Line
import xenakis.impl.Decimal
import xenakis.impl.zero
import xenakis.model.score.ObjectPosition
import xenakis.ui.score.ScoreObjectView
import xenakis.ui.score.ScorePane
import xenakis.ui.score.TimeBlock
import xenakis.ui.score.TimeCodeView

class PlayHead(private val context: Context) {
    private var attached = false

    var currentTime = START
        private set(value) {
            field = value
            if (attached) {
                Platform.runLater {
                    context[TimeCodeView].displayTime(value)
                }
            }
        }

    private val playHead = Line().styleClass("play-head")

    private lateinit var timeBlock: TimeBlock
    lateinit var pane: Pane
        private set

    init {
        playHead.viewOrder = -500.0
        playHead.strokeWidth = PLAY_HEAD_WIDTH
    }

    val absoluteStartPosition: ObjectPosition
        get() = when (val pane = pane) {
            is ScorePane -> pane.absolutePosition
            is ScoreObjectView -> pane.absolutePosition
            else -> throw IllegalStateException("Cannot get position of $pane, it is not a ScorePane or ScoreObjectView")
        }

    fun <T> attachTo(target: T, verticalPadding: Double) where T : TimeBlock, T : Pane {
        (playHead.parent as? Pane)?.children?.remove(playHead)
        playHead.startY = verticalPadding
        if (playHead.endYProperty().isBound) playHead.endYProperty().unbind()
        playHead.endYProperty().bind(target.heightProperty().subtract(verticalPadding))
        movePlayHead(START)
        timeBlock = target
        pane = target
        attached = true
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
        if (!attached) return
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