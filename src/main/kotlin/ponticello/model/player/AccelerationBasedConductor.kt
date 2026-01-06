package ponticello.model.player

import com.illposed.osc.OSCMessageEvent
import hextant.context.Context
import javafx.application.Platform
import ponticello.impl.Decimal
import ponticello.impl.plus
import ponticello.impl.toDecimal
import ponticello.impl.toSeconds
import ponticello.model.obj.project
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.sc.client.getArgument
import reaktive.value.now
import java.io.File
import java.util.*
import kotlin.math.tanh

class AccelerationBasedConductor private constructor(
    player: ScorePlayer, options: ConductorOptions
) : Conductor(player, options) {
    override fun startVideoAnalysis(pythonExe: String, rubatoDir: File, startAt: Long): Process {
        val scriptPath = rubatoDir.resolve("live.py").absolutePath
        val extraArguments = options.extraArguments.now.split(" ").toTypedArray()
        return ProcessBuilder()
            .command(
                pythonExe, scriptPath,
                "--udp-port", options.port.now.toString(),
                "--start-at", startAt.toString(),
                "--show-video",
                *extraArguments
            )
            .directory(rubatoDir)
            .inheritIO()
            .start()
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        super.acceptMessage(event)
        if (!isActive.now) return
        when (event.message.address) {
            "/beat" -> {
                val timestamp = event.message.getArgument<Long>(0, "timestamp")?.toSeconds() ?: return
                onBeat(timestamp)
            }
        }
    }

    private fun onBeat(timestamp: Decimal) {
        val meter = activeMeter ?: return
//        println("Beat at $timestamp. Magnitude: $magnitude, Velocity: $velocity.")
        beat(timestamp)

        val conductorTempo = currentTempo()
        if (nBeats == beatsPerBar) {
            val warp = (conductorTempo / meter.beatsPerMinute.now).coerceIn(0.5.toDecimal(), 2.0.toDecimal())
            println("Time warp: $warp")
            setTimeWarp(warp)
        } else if (nBeats > beatsPerBar) {
            println("Conductor tempo: $conductorTempo")
            val scoreTime = player.playHead.currentTime
            val dist = (conductorTime - scoreTime).value / 2
            println("Distance: $dist")
            val nudge = tanh(dist) / 2
            println("Nudge: $nudge")
            Platform.runLater {
                val warp = clock.timeWarp.now
                val totalWarp = (warp + nudge).coerceIn(0.5.toDecimal(), 2.0.toDecimal())
                println("New time warp: $warp + $nudge = $totalWarp")
                setTimeWarp(totalWarp)
            }
        }
    }

    companion object {
        private val byPlayer = WeakHashMap<ScorePlayer, AccelerationBasedConductor>()

        fun get(context: Context): AccelerationBasedConductor {
            val player = context[ScorePlayer.MAIN]
            return byPlayer.getOrPut(player) {
                val options = context.project[PLAYBACK_SETTINGS].conductorOptions
                AccelerationBasedConductor(player, options)
            }
        }
    }
}