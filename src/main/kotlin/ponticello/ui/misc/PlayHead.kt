package ponticello.ui.misc

import fxutils.styleClass
import javafx.application.Platform
import javafx.scene.shape.Line
import ponticello.impl.*
import ponticello.model.player.ScorePlayer
import ponticello.model.score.ObjectPosition
import ponticello.ui.score.NavigableScorePane
import ponticello.ui.score.RootScorePane
import ponticello.ui.score.ScorePane
import ponticello.ui.score.TimeCodeView
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveVariable

class PlayHead {
    private val attached = mutableListOf<AttachedScorePane>()
    private var _player: ScorePlayer? = null
    private var _canMoveManually = reactiveVariable(true)
    private lateinit var playerObserver: Observer

    var currentTime = 0.0.asTime
        private set(value) {
            field = value.withPrecision(ObjectPosition.TIME_PRECISION)
        }

    var player: ScorePlayer
        get() = checkNotNull(_player) { "No player attached to the play head" }
        set(p) {
            check(_player == null) { "A player is already attached to the play head" }
            _player = p
            playerObserver = p.isScheduled.forEach { scheduled ->
                _canMoveManually.set(!scheduled)
            }
        }

    val canMoveManually: ReactiveValue<Boolean> get() = _canMoveManually

    fun attachTo(pane: RootScorePane) {
        val playHead = Line() styleClass "play-head"
        if (pane.playHeadStyle != null) playHead.styleClass(pane.playHeadStyle)
        playHead.viewOrder = -500.0
        playHead.strokeWidth = PLAY_HEAD_WIDTH
        playHead.isMouseTransparent = true
        playHead.endYProperty().bind(pane.heightProperty())
        playHead.startY = 0.0
        attached.add(AttachedScorePane(pane, pane.timeCodeView, playHead))
    }

    fun movePlayHead(pos: Decimal) {
        currentTime = pos
        Platform.runLater { updatePosition() }
    }

    fun centerInScorePane() {
        for ((pane) in attached) {
            if (pane is NavigableScorePane) {
                val displayStart = (currentTime - pane.displayedDuration * 0.5).coerceAtLeast(zero)
                val displayEnd = displayStart + pane.displayedDuration
                pane.display(displayStart, displayEnd)
            }
        }
    }

    fun movePlayHeadToStart() {
        movePlayHead(0.0.asTime)
    }

    fun updatePosition() {
        for ((pane, timeCodeView, playHead) in attached) {
            if (!pane.children.contains(playHead)) pane.children.add(playHead)
            playHead.layoutX = pane.getX(currentTime)
            if (!canMoveManually.now) {
                timeCodeView.displayTime(currentTime)
            }
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