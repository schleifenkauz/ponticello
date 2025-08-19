package ponticello.model.player

import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.asTime
import ponticello.impl.zero
import ponticello.model.GlobalSettings
import ponticello.model.flow.AudioFlows
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ObjectPosition
import ponticello.sc.client.getArgument
import reaktive.value.now

class PlaybackMessageListener(
    private val settings: GlobalSettings,
    private val objects: ScoreObjectRegistry,
    private val flows: AudioFlows,
) : OSCMessageListener {
    override fun acceptMessage(event: OSCMessageEvent) = ScorePlayer.execute {
        when (event.message.address) {
            "/play" -> {
                val name = event.message.getArgument<String>(1, "name") ?: return@execute
                val timestamp = event.message.getArgument<Float>(2, "timestamp") ?: return@execute
                val playerId = event.message.getArgument<Int>(3, "playerId") ?: return@execute
                val player = ScorePlayer.getById(playerId)
                playObject(name, timestamp.toDouble().asTime, player)
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

    private fun playObject(name: String, timestamp: Decimal, player: ScorePlayer) {
        val obj = objects.getOrNull(name)
        if (obj == null) {
            Logger.warn("Could not find object with name $name", Logger.Category.Playback)
            return
        }
        val y = obj.liveConfig.yPosition.now
        val scoreTime = player.playHead.currentTime
        val serverLatency = settings.serverLatency.now
        val scorePosition = ObjectPosition(scoreTime, y)
        ScorePlayer.execute {
            player.scheduler.scheduleObject(
                obj, instance = null, player, cutoff =  zero, scorePosition,
                serverLatency, timestamp, absolute = true, emptyMap()
            )
        }
    }
}