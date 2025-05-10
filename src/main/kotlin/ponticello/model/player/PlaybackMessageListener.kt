package ponticello.model.player

import reaktive.value.now
import ponticello.impl.Logger
import ponticello.impl.zero
import ponticello.model.flow.AudioFlows
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ObjectPosition
import ponticello.sc.client.SuperColliderListener

class PlaybackMessageListener(
    private val objects: ScoreObjectRegistry,
    private val flows: AudioFlows,
    private val player: ScorePlayer,
) : SuperColliderListener {
    override fun onMessage(path: String, content: String) = ScorePlayer.execute{
        when {
            path.startsWith("/play") -> playObject(content)

            path.startsWith("/pause") -> {

            }

            path.startsWith("/resume") -> {

            }
        }
    }

    private fun playObject(name: String) {
        val obj = objects.getOrNull(name)
        if (obj == null) {
            Logger.warn("Could not find object with name $name", Logger.Category.Playback)
            return
        }
        val y = obj.liveConfig.yPosition.now
        val pos = ObjectPosition(player.playHead.currentTime, y)
        ScorePlayer.execute {
            player.scheduler.scheduleObject(obj, pos, cutoff = zero, player)
        }
    }
}