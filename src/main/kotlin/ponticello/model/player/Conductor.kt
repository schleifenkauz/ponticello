package ponticello.model.player

import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.transport.OSCPortIn
import javafx.application.Platform
import ponticello.impl.*
import ponticello.model.obj.MeterObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.TempoGridObject
import ponticello.model.score.TimeUnit
import ponticello.sc.client.OSCSuperColliderClient
import ponticello.sc.client.getArgument
import reaktive.event.unitEvent
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.io.File
import java.util.*
import kotlin.math.pow
import kotlin.math.tanh
import kotlin.random.Random

class Conductor private constructor(
    private val player: ScorePlayer,
    val port: ReactiveVariable<Int>,
) : OSCMessageListener {
    val countdownTime = reactiveVariable(5)

    private var conductorTime = 0.0.asTime
    private val beatTimes: Deque<Decimal> = LinkedList()
    private val barPosition = reactiveVariable(0)
    private val active = reactiveVariable(false)
    private val scheduled = reactiveVariable(false)
    private var startingMeter: MeterObject? = null
    val beatsPerBar: Int
        get() = startingMeter?.beatsPerBar?.now ?: 0
    private var analysisProcess: Process? = null
    private var receiver: OSCPortIn? = null

    private val clock get() = player.getClock()
    private val activeMeter get() = clock.activeMeter
    val isActive: ReactiveBoolean get() = active
    val isScheduled: ReactiveBoolean get() = scheduled

    private val beat = unitEvent()
    val onBeat get() = beat.stream

    val beats get() = barPosition

    private val playerStopObserver = player.isPlaying.observe { _, before, now ->
        if (before && !now) {
            stop()
        }
    }

    fun start(): Boolean {
        startingMeter = player.score.activeInstances(zero..one)
            .map(ScoreObjectInstance::obj)
            .filterIsInstance<TempoGridObject>()
            .firstOrNull()?.meter?.get() ?: return false

        scheduled.set(true)

        val receiver = OSCPortIn(port.now)
        receiver.startListening()
        receiver.dispatcher.addListener(OSCSuperColliderClient.ALL_MESSAGES, this)

        val rubatoDir = File(System.getProperty("user.home"), "dev/rubato")
        val pythonExe = rubatoDir.resolve(".venv/bin/python").absolutePath
        val scriptPath = rubatoDir.resolve("live.py").absolutePath
        val startAt = System.currentTimeMillis() / 1000.0 + countdownTime.now
        analysisProcess = ProcessBuilder()
            .command(
                pythonExe, scriptPath,
                "--udp-port", port.now.toString(),
                "--start-at", startAt.toString(),
                "--show-video"
            )
            .directory(rubatoDir)
            .inheritIO()
            .start()
        analysisProcess!!.onExit().thenAccept {
            doStop()
        }

        return true
    }

    fun stop() {
        analysisProcess?.destroy()
    }

    private fun doStop() {
        beatTimes.clear()
        barPosition.now = 0
        scheduled.set(false)
        active.set(false)
        analysisProcess = null
        receiver?.stopListening()
        receiver = null
        player.pause()
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        if (event.message.address == "/started") {
            active.set(true)
            return
        }
        if (!active.now) return
        val meter = activeMeter?.meter ?: startingMeter ?: return
        when (event.message.address) {
            "/exited" -> {
                scheduled.set(false)
                active.set(false)
            }

            "/tempo" -> {
                val estimatedTempo = event.message.getArgument<String>(1, "estimated tempo")?.parseDecimal() ?: return
                val scoreTempo = meter.beatsPerMinute.now
                clock.timeWarp.now = (estimatedTempo / scoreTempo)
            }

            "/beat" -> {
                val timestamp = event.message.getArgument<Long>(0, "timestamp")?.toSeconds() ?: return
                val magnitude = event.message.getArgument<Float>(1, "magnitude") ?: return
                val velocity = event.message.getArgument<Float>(2, "velocity") ?: return
//                println("Beat at $timestamp. Magnitude: $magnitude, Velocity: $velocity.")
                if (beatTimes.isNotEmpty()) {
                    barPosition.now = (barPosition.now + 1) % beatsPerBar
                }
                if (beatTimes.size == beatsPerBar + 1) {
                    conductorTime += meter.getDuration(TimeUnit.Beats)
                }
                while (beatTimes.size > beatsPerBar) beatTimes.removeFirst()
                beatTimes.addLast(timestamp)
                if (beatTimes.size == beatsPerBar) {
                    player.play()
                } else if (beatTimes.size == beatsPerBar + 1) {
                    val deltas = beatTimes.zipWithNext { a, b -> b - a }
                    val conductorTempo = deltas.fold(zero, Decimal::plus) / beatsPerBar * 60
                    println("Conductor tempo: $conductorTempo")
                    val warp = conductorTempo / meter.beatsPerMinute.now
                    val scoreTime = player.currentTime
                    val nudge = TEMPO_NUDGE.pow(tanh((conductorTime - scoreTime).value))
                    Platform.runLater {
                        val totalWarp = (warp * nudge).coerceIn(0.5.toDecimal(), 2.0.toDecimal())
                        println("New time warp: $warp * $nudge = $totalWarp")
                        clock.timeWarp.set(totalWarp)
                    }
                }
            }
        }
    }

    companion object {
        private const val TEMPO_NUDGE = 1.5

        private val byPlayer = WeakHashMap<ScorePlayer, Conductor>()

        fun forPlayer(player: ScorePlayer): Conductor = byPlayer.getOrPut(player) {
            val port = 57140 + Random.nextInt(10)
            Conductor(player, reactiveVariable(port))
        }
    }
}