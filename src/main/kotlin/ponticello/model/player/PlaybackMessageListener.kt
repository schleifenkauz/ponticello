package ponticello.model.player

import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import ponticello.impl.*
import ponticello.model.PlaybackSettings
import ponticello.model.flow.AudioFlows
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ObjectPosition
import ponticello.sc.client.getArgument
import reaktive.value.now

class PlaybackMessageListener(
    private val settings: PlaybackSettings,
    private val objects: ScoreObjectRegistry,
    private val flows: AudioFlows,
) : OSCMessageListener {
    override fun acceptMessage(event: OSCMessageEvent) = ScorePlayer.execute {
        when (event.message.address) {
            "/play" -> {
                val name = event.message.getArgument<String>(1, "name") ?: return@execute
                val timestamp = event.message.getArgument<Float>(2, "timestamp") ?: return@execute
                val absoluteY = event.message.getArgument<Float>(3, "absoluteY") ?: return@execute
                val playerId = event.message.getArgument<Int>(4, "playerId") ?: return@execute
                val player = ScorePlayer.getById(playerId)
                playObject(name, timestamp.toDouble().asTime, absoluteY.toDouble().toDecimal(), player)
            }

            "/pause" -> { //TODO add the corresponding expression types
                val name = event.message.getArgument<String>(1, "name") ?: return@execute
                val flow = flows.getFlow(name) ?: return@execute
                flow.setActive(false)
            }

            "/resume" -> {
                val name = event.message.getArgument<String>(1, "name") ?: return@execute
                val flow = flows.getFlow(name) ?: return@execute
                flow.setActive(true)
            }
        }
    }

    private fun playObject(name: String, timestamp: Decimal, absoluteY: Decimal, player: ScorePlayer) {
        val obj = objects.getOrNull(name)
        if (obj == null) {
            Logger.warn("Could not find object with name $name", Logger.Category.Playback)
            return
        }
        val scoreTime = player.playHead.currentTime
        val serverLatency = settings.serverLatency.now
        val scorePosition = ObjectPosition(scoreTime, absoluteY)
        ScorePlayer.execute {
            player.scheduler.scheduleObject(
                obj, instance = null, player, cutoff =  zero, scorePosition,
                serverLatency, timestamp, absolute = true, emptyMap()
            )
        }
    }
}