package xenakis.model.player

import bundles.PublicProperty
import bundles.publicProperty
import javafx.scene.layout.Pane
import reaktive.value.now
import xenakis.model.Settings
import xenakis.model.score.ObjectPosition
import xenakis.model.score.Score
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectInstance
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.misc.PlayHead
import xenakis.ui.score.ScoreObjectGroupView
import xenakis.ui.score.ScoreObjectView
import xenakis.ui.score.ScoreView

class PlaybackManager(private val scoreView: ScoreView) {
    val env: ScorePlayEnv = ScorePlayEnv(scoreView.context[Settings])
    val playHead = PlayHead()
    val recorder = Recorder(scoreView.context)
    lateinit var player: ScorePlayer
        private set
    private lateinit var events: ScoreEventCollector
    private var isAttached = false

    val context get() = scoreView.context

    init {
        attachToMainScore()
    }

    fun isAttachedTo(target: Pane) = playHead.pane == target

    private fun detach() {
        if (!isAttached) return
        player.close()
        events.removeListeners()
        isAttached = false
    }

    fun attachToMainScore() {
        playHead.attachTo(scoreView, verticalPadding = 20.0)
        attachTo(scoreView.score)
    }

    fun attachToView(view: ScoreObjectView) {
        val score = if (view is ScoreObjectGroupView) view.obj.score else simpleScore(view.instance.obj)
        playHead.attachTo(view, verticalPadding = 0.0)
        attachTo(score)
    }

    private fun attachTo(score: Score) {
        detach()
        events = ScoreEventCollector(score, env)
        player = ScorePlayer(score, playHead, scoreView.context[SuperColliderClient], env, events, recorder)
        events.player = player
        isAttached = true
    }

    fun movePlayHeadToStart() {
        if (!player.isPlaying.now) {
            playHead.movePlayHeadToStart()
        }
    }

    companion object : PublicProperty<PlaybackManager> by publicProperty("PlaybackManager") {
        private fun simpleScore(obj: ScoreObject): Score {
            val inst = ScoreObjectInstance(obj, ObjectPosition.ZERO)
            val score = Score(mutableListOf(inst))
            score.initialize(obj.context, null)
            return score
        }
    }
}