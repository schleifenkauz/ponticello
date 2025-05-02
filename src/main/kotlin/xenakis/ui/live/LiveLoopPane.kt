package xenakis.ui.live

import bundles.set
import fxutils.hspace
import fxutils.infiniteSpace
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import xenakis.model.live.LiveLoopObject
import xenakis.ui.actions.PlaybackActions
import xenakis.ui.actions.toolbarPart
import xenakis.ui.score.ScoreObjectDuplicator
import xenakis.ui.score.ScorePane
import xenakis.ui.score.TimeCodeView

class LiveLoopPane(private val obj: LiveLoopObject) : VBox() {
    private val context = obj.context
    private val timeCodeView = TimeCodeView()
    private val scorePane = LiveScorePane(obj)

    init {
        setupPlayback()
        setVgrow(scorePane, Priority.ALWAYS)
        children.addAll(createPlaybackBar(), scorePane)
    }

    private fun setupPlayback() {
        obj.player.attachToScoreView(scorePane)
        context[TimeCodeView] = timeCodeView
        context[ScorePane.CURRENT_ROOT] = scorePane
        context[ScoreObjectDuplicator].registerRootPane(scorePane)
    }

    private fun createPlaybackBar() = HBox(
        infiniteSpace(),
        toolbarPart(PlaybackActions.withContext(obj.player)),
        hspace(20.0),
        timeCodeView,
        infiniteSpace()
    )
}