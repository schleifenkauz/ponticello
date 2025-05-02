package xenakis.model.live

import bundles.set
import hextant.context.Context
import hextant.context.extend
import hextant.undo.UndoManager
import kotlinx.serialization.SerialName
import reaktive.value.ReactiveVariable
import xenakis.model.player.ScorePlayer

class LiveLoopObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
) : LiveObject() {
    lateinit var player: ScorePlayer
        private set

    override fun initialize(context: Context) {
        player = ScorePlayer(context)
        val myContext = context.extend {
            set(UndoManager, UndoManager.newInstance())
            set(ScorePlayer.CURRENT, player)
        }
        super.initialize(myContext)
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