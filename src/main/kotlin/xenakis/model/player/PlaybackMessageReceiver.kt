package xenakis.model.player

import hextant.context.Context
import reaktive.Observer
import xenakis.impl.Logger
import xenakis.impl.one
import xenakis.impl.zero
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.ObjectPosition
import xenakis.sc.client.SuperColliderClient

class PlaybackMessageReceiver(private val context: Context) {
    private fun registerPlayObserver(): Observer = context[SuperColliderClient].onPlayObj.observe { _, name ->
        val obj = context[ScoreObjectRegistry].getOrNull(name)
        if (obj == null) {
            Logger.warn("Could not find object with name $name", Logger.Category.Playback)
            return@observe
        }
        val player = context[ScorePlayer.CURRENT]
        val pos = ObjectPosition(player.currentTime, one)
        player.scheduleObject(obj, pos, cutoff = zero)
        //TODO schedule freeing/stopping the scheduled object after its duration
    }
}