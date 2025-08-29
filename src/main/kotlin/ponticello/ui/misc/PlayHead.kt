package ponticello.ui.misc

import fxutils.styleClass
import javafx.application.Platform
import javafx.scene.shape.Line
import ponticello.impl.Decimal
import ponticello.impl.asTime
import ponticello.impl.withPrecision
import ponticello.model.player.ScorePlayer
import ponticello.model.score.ObjectPosition
import ponticello.ui.score.ScorePane
import ponticello.ui.score.TimeCodeView
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.not

class PlayHead {
    private val attached = mutableListOf<AttachedScorePane>()
    private val _player: ReactiveVariable<ScorePlayer?> = reactiveVariable(null)
    var currentTime = 0.0.asTime
        private set(value) {
            field = value.withPrecision(ObjectPosition.TIME_PRECISION)
        }

    val player: ReactiveValue<ScorePlayer?> get() = _player

    fun setPlayer(player: ScorePlayer) {
        check(_player.now == null) { "A player is already attached to the play head" }
        _player.now = player
    }

    val canMoveManually = _player.flatMap { p -> p?.isScheduled?.not() ?: reactiveValue(true) }

    fun attachTo(pane: ScorePane, timeCodeView: TimeCodeView) {
        val playHead = Line() styleClass "play-head"
        playHead.viewOrder = -500.0
        playHead.strokeWidth = PLAY_HEAD_WIDTH
        playHead.isMouseTransparent = true
        playHead.endYProperty().bind(pane.heightProperty().subtract(20))
        playHead.startY = 10.0
        attached.add(AttachedScorePane(pane, timeCodeView, playHead))
    }

    fun movePlayHead(pos: Decimal) {
        currentTime = pos
        Platform.runLater { updatePosition() }
    }

    fun movePlayHeadToStart() {
        movePlayHead(0.0.asTime)
    }

    fun updatePosition() {
        for ((pane, timeCodeView, playHead) in attached) {
            if (!pane.children.contains(playHead)) pane.children.add(playHead)
            playHead.layoutX = pane.getX(currentTime)
            timeCodeView.displayTime(currentTime)
        }
    }

    private data class AttachedScorePane(
        val pane: ScorePane,
        val timeCodeView: TimeCodeView,
        val line: Line,
    )

    companion object {
        const val PLAY_HEAD_WIDTH = 2.0
    }
}