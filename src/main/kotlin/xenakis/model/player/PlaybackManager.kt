package xenakis.model.player

import bundles.PublicProperty
import bundles.publicProperty
import javafx.scene.layout.Pane
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.Settings
import xenakis.model.flow.AudioFlowGraph
import xenakis.model.flow.AudioFlows
import xenakis.model.flow.NodeTree
import xenakis.model.score.ObjectPosition
import xenakis.model.score.Score
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectInstance
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.misc.PlayHead
import xenakis.ui.score.ScoreObjectGroupView
import xenakis.ui.score.ScoreObjectView
import xenakis.ui.score.ScoreView

class PlaybackManager(private val scoreView: ScoreView, flows: AudioFlows) {
    val playHead = PlayHead()
    val recorder = Recorder(scoreView.context)
    val nodeTree = NodeTree(scoreView.context[SuperColliderClient])
    val graph = AudioFlowGraph(flows, nodeTree)
    lateinit var player: ScorePlayer
        private set
    lateinit var events: ScoreEventCollector
        private set
    private var isAttached = false

    private var isPlayingObserver: Observer? = null
    private val _isPlaying = reactiveVariable(false)

    val isPlaying: ReactiveValue<Boolean> = _isPlaying

    val loopingActivated = reactiveVariable(false)

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
        val settings = context[Settings]
        events = ScoreEventCollector(score, graph, settings)
        player = ScorePlayer(score, this, scoreView.context[SuperColliderClient], settings)
        isPlayingObserver?.kill()
        isPlayingObserver = _isPlaying.bind(player.isPlaying)
        events.player = player
        isAttached = true
    }

    fun movePlayHeadToStart() {
        if (!isPlaying.now) {
            playHead.movePlayHeadToStart()
        }
    }

    companion object : PublicProperty<PlaybackManager> by publicProperty("PlaybackManager") {
        private fun simpleScore(obj: ScoreObject): Score {
            val inst = ScoreObjectInstance(obj, ObjectPosition.ZERO)
            val score = Score(mutableListOf(inst))
            score.initialize(obj.context, obj)
            return score
        }
    }
}