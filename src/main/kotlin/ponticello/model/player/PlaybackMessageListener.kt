package ponticello.model.player

import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import javafx.application.Platform
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.asTime
import ponticello.impl.toDecimal
import ponticello.model.flow.AudioFlows
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ObjectPosition
import ponticello.sc.client.getArgument
import ponticello.ui.actions.PlaybackActions
import reaktive.value.now

class PlaybackMessageListener(
    private val objects: ScoreObjectRegistry,
    private val flows: AudioFlows,
    private val mainPlayer: ScorePlayer,
) : OSCMessageListener {
    override fun acceptMessage(event: OSCMessageEvent) = ScorePlayer.execute {
        when (event.message.address) {
            "/start_play" -> Platform.runLater {
                mainPlayer.play()
            }

            "/pause_play" -> Platform.runLater {
                mainPlayer.pause()
            }

            "/toggle_play" -> Platform.runLater {
                mainPlayer.togglePlaying()
            }

            "/stop_playback" -> Platform.runLater {
                PlaybackActions.stopAll(mainPlayer)
            }

            "/go_to_start" -> Platform.runLater {
                if (!(mainPlayer.isPlaying.now)) {
                    mainPlayer.playHead.movePlayHeadToStart()
                }
            }

            "/play" -> {
                val name = event.message.getArgument<String>(1, "name") ?: return@execute
                val timestamp = event.message.getArgument<Float>(2, "timestamp") ?: return@execute
                val absoluteY = event.message.getArgument<Float>(3, "absoluteY") ?: return@execute
                val playerId = event.message.getArgument<Int>(4, "playerId") ?: return@execute
                val player = ScorePlayer.getById(playerId)
                playObject(name, timestamp.toDouble().asTime, absoluteY.toDecimal(), player)
            }

            "/pause" -> { //TODO add the corresponding expression types
                val name = event.message.getArgument<String>(1, "name") ?: return@execute
                val flow = flows.getFlow(name) ?: return@execute
                Platform.runLater {
                    flow.setActive(false)
                }
            }

            "/resume" -> {
                val name = event.message.getArgument<String>(1, "name") ?: return@execute
                val flow = flows.getFlow(name) ?: return@execute
                Platform.runLater {
                    flow.setActive(true)
                }
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
        val scorePosition = ObjectPosition(scoreTime, absoluteY)
        val info = ObjectPlaybackInfo(scorePosition, player)
        player.scheduler.scheduleObject(obj, info, timestamp, absolute = true)
    }
}