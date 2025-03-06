package xenakis.ui.actions

import fxutils.actions.Action
import fxutils.actions.isShiftDown
import fxutils.mouseX
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.value.now
import xenakis.impl.asTime
import xenakis.model.player.PlaybackManager
import xenakis.ui.score.ScoreView

object ScoreNavigationActions : Action.Collector<ScoreView>({
    addAction("Move View To Start") {
        description("Moves the displayed portion of the score to the start")
        shortcut("HOME")
        executes { view -> view.display(0.0.asTime, view.displayedDuration) }
    }
    addAction("Display Whole Score") {
        shortcut("Ctrl+HOME")
        executes { view -> view.displayWholeScore() }
    }
    addAction("Move to Cursor") {
        description("Move displayed portion of the score to playback cursor")
        shortcut("Shift+SPACE")
        executes { view ->
            val playback = view.context[PlaybackManager]
            val t = playback.playHead.currentTime
            view.display(t, view.displayedDuration + t)
        }
    }
    addAction("Move playback cursor to start") {
        shortcut("Ctrl+Shift?+DIGIT0")
        icon(Material2MZ.SKIP_PREVIOUS)
        executes { view, ev ->
            val playback = view.context[PlaybackManager]
            if (ev.isShiftDown()) {
                if (playback.player.isPlaying.now) return@executes
                view.display(0.0.asTime, view.displayedDuration)
                playback.attachToMainScore()
            }
            playback.movePlayHeadToStart()
        }
    }
    addAction("Zoom in") {
        shortcut("Ctrl+PLUS")
        icon(Material2MZ.ZOOM_IN)
        executes { view ->
            view.zoom(1 / 1.2, view.mouseX)
        }
    }
    addAction("Zoom out") {
        shortcut("Ctrl+MINUS")
        icon(Material2MZ.ZOOM_OUT)
        executes { view ->
            view.zoom(1.2, view.mouseX)
        }
    }
})