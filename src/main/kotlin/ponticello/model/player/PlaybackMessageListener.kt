package ponticello.model.player

import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import ponticello.impl.Logger
import ponticello.impl.zero
import ponticello.model.flow.AudioFlows
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ObjectPosition
import ponticello.sc.client.getArgument
import reaktive.value.now

class PlaybackMessageListener(
    private val objects: ScoreObjectRegistry,
    private val flows: AudioFlows,
    private val player: ScorePlayer,
) : OSCMessageListener {
    override fun acceptMessage(event: OSCMessageEvent) = ScorePlayer.execute {
        when (event.message.address) {
            "/play" -> {
                val name = event.message.getArgument<String>(1, "name") ?: return@execute
                playObject(name)
            }

            "/pause" -> {

            }

            "/resume" -> {

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