package xenakis.model.live

import bundles.set
import hextant.context.Context
import hextant.context.extend
import hextant.undo.UndoManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import xenakis.model.player.ScorePlayer
import xenakis.model.score.Score

@Serializable
class LiveLoopObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val config: LoopConfig,
    val rootScore: Score,
) : LiveObject() {
    @Transient
    lateinit var player: ScorePlayer
        private set

    override val quantization: Quantization
        get() = config.getQuantization()

    override fun initialize(context: Context) {
        player = ScorePlayer(context)
        val myContext = context.extend {
            set(UndoManager, UndoManager.newInstance())
            set(ScorePlayer.CURRENT, player)
        }
        super.initialize(myContext)
        config.initialize(myContext)
        rootScore.initialize(myContext, this)
    }

    override fun doActivate() {
        player.play()
    }

    override fun doDeactivate() {
        player.pause()
    }

    override fun doReset() {
        player.movePlayHeadToStart()
    }

    override fun sync() {
        player.events.resetEvents()
    }
}