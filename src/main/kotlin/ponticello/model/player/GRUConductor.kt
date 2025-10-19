package ponticello.model.player

import com.illposed.osc.OSCMessageEvent
import hextant.context.Context
import ponticello.impl.*
import ponticello.model.obj.project
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.model.score.TimeUnit
import ponticello.sc.client.getArgument
import reaktive.value.now
import java.io.File
import java.util.*

class GRUConductor private constructor(player: ScorePlayer, options: ConductorOptions) : Conductor(player, options) {
    private var lastPrediction = Float.MAX_VALUE

    override fun startVideoAnalysis(pythonExe: String, rubatoDir: File, startAt: Long): Process {
        val scriptPath = rubatoDir.resolve("live-gru.py").absolutePath
        val argsString = options.extraArguments.now
        val extraArguments = argsString.split(" ").filter(String::isNotBlank).toTypedArray()
        return ProcessBuilder()
            .command(
                pythonExe, scriptPath,
                "--udp-port", options.port.now.toString(),
                "--start-at", startAt.toString(),
                "--model", options.modelName.now,
                "--input", options.videoInput.now,
                "-bt", options.beatThreshold.now.toString(),
                *extraArguments
            )
            .directory(rubatoDir)
            .inheritIO()
            .start()
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        super.acceptMessage(event)
        val meter = activeMeter
        if (!isActive.now || meter == null) return
        when (event.message.address) {
            "/next_beat" -> {
                val timestamp = event.message.getArgument<Long>(0, "timestamp")?.toSeconds() ?: return
                val predictedTimeToNextBeat = event.message.getArgument<Float>(1, "Time to next beat") ?: return
                val beatProbability = event.message.getArgument<Float>(2, "Beat probability") ?: return
                val now = System.currentTimeMillis()
                if (beatProbability >= options.beatThreshold.now.value && now > lastBeatMs + 500) {
                    beat(timestamp)
                } else if (player.isScheduled.now) {
                    if (predictedTimeToNextBeat > lastPrediction) {
                        clock.timeWarp.now = currentTempo() / meter.beatsPerMinute.now
                    } else {
                        val conductorTime = conductorTime
                        val playerTime = player.currentTime
                        val scoreTimeToNextBeat = (conductorTime + meter.getDuration(TimeUnit.Beats) - playerTime)
                            .coerceAtLeast(0.1.toDecimal())
                        val pred = (predictedTimeToNextBeat.toDouble() + 0.1).coerceAtLeast(0.01)
                        val warp = scoreTimeToNextBeat / pred
                        val alpha = options.warpFactor.now
                        clock.timeWarp.now = (warp * alpha).coerceAtMost(1.5.toDecimal())
                    }
                }
                lastPrediction = predictedTimeToNextBeat
            }
        }
    }

    companion object {
        private val byPlayer = WeakHashMap<ScorePlayer, GRUConductor>()

        fun get(context: Context): GRUConductor {
            val player = context[ScorePlayer.MAIN]
            return byPlayer.getOrPut(player) {
                val options = context.project[PLAYBACK_SETTINGS].conductorOptions
                GRUConductor(player, options)
            }
        }
    }
}