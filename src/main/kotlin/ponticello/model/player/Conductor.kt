package ponticello.model.player

import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.transport.OSCPortIn
import fxutils.styleClass
import javafx.application.Platform
import javafx.scene.shape.Line
import ponticello.impl.*
import ponticello.model.obj.MeterObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.TempoGridObject
import ponticello.model.score.TimeUnit
import ponticello.sc.client.OSCSuperColliderClient
import ponticello.sc.client.getArgument
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.score.ScorePane
import reaktive.event.event
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.tanh
import kotlin.random.Random

class Conductor private constructor(
    private val player: ScorePlayer,
    private val scorePane: ScorePane,
    val port: ReactiveVariable<Int>,
) : OSCMessageListener {
    val countdownTime = reactiveVariable(5)

    private var conductorTime = 0.0.asTime
        set(value) {
            field = value
            Platform.runLater {
                conductorTimeIndicator.startX = scorePane.getWidth(value)
                conductorTimeIndicator.endX = conductorTimeIndicator.startX
            }
        }

    private val beatTimes: Deque<Decimal> = LinkedList()
    private var nBeats = 0
    val barPosition get() = if (beatsPerBar != 0) nBeats % beatsPerBar else 0
    private val active = reactiveVariable(false)
    private val scheduled = reactiveVariable(false)
    private var startingMeter: MeterObject? = null
    val beatsPerBar: Int
        get() = startingMeter?.beatsPerBar?.now ?: 0
    private var analysisProcess: Process? = null
    private var receiver: OSCPortIn? = null

    private val clock get() = player.getClock()
    private val activeMeter get() = clock.activeMeter?.meter ?: startingMeter
    val isActive: ReactiveBoolean get() = active
    val isScheduled: ReactiveBoolean get() = scheduled

    private val beat = event<Int>()
    val onBeat get() = beat.stream

    private val conductorTimeIndicator = Line().styleClass("play-head", "conductor-time")

    init {
        conductorTimeIndicator.endYProperty().bind(scorePane.heightProperty())
    }

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

        if (conductorTimeIndicator !in scorePane.children) {
            scorePane.children.add(conductorTimeIndicator)
        }

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
        nBeats = 0
        conductorTime = 0.0.asTime
        scheduled.set(false)
        active.set(false)
        analysisProcess = null
        receiver?.stopListening()
        receiver = null
        player.pause()
        Platform.runLater {
            scorePane.children.remove(conductorTimeIndicator)
        }
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        if (event.message.address == "/started") {
            active.set(true)
            return
        }
        if (!active.now) return
        when (event.message.address) {
            "/exited" -> {
                scheduled.set(false)
                active.set(false)
            }

            "/beat" -> {
                val timestamp = event.message.getArgument<Long>(0, "timestamp")?.toSeconds() ?: return
                val magnitude = event.message.getArgument<Float>(1, "magnitude") ?: return
                val velocity = event.message.getArgument<Float>(2, "velocity") ?: return
                onBeat(timestamp, magnitude, velocity)
            }
        }
    }

    private fun onBeat(timestamp: Decimal, magnitude: Float, velocity: Float) {
        val meter = activeMeter ?: return
//        println("Beat at $timestamp. Magnitude: $magnitude, Velocity: $velocity.")
        nBeats++
        if (nBeats > beatsPerBar + 1) {
            conductorTime += meter.getDuration(TimeUnit.Beats)
        }
        beat.fire(barPosition)

        beatTimes.addLast(timestamp)
        while (beatTimes.size > beatsPerBar) beatTimes.removeFirst()
        val deltas = beatTimes.zipWithNext { a, b -> b - a }
        val conductorTempo = deltas.size / deltas.fold(zero, Decimal::plus) * 60
        val warp = (conductorTempo / meter.beatsPerMinute.now).pow(0.5)

        if (nBeats == beatsPerBar) {
            val beatDur = 60 / conductorTempo
            println("Beat duration: $beatDur")
            val delay = ((beatDur - player.lookAhead) * 1000).toLong()
            println("Scheduling the player with a delay of $delay ms.")
            scheduler.schedule(player::play, delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            val totalWarp = warp.coerceIn(0.5.toDecimal(), 2.0.toDecimal())
            println("Time warp: $totalWarp")
            clock.timeWarp.set(totalWarp)
        } else if (nBeats > beatsPerBar) {
            println("Conductor tempo: $conductorTempo")
            val scoreTime = player.playHead.currentTime
            val dist = (conductorTime - scoreTime).value / 2
            println("Distance: $dist")
            val nudge = TEMPO_NUDGE.pow(tanh(dist))
            println("Nudge: $nudge")
            Platform.runLater {
                val totalWarp = (warp * nudge).coerceIn(0.5.toDecimal(), 2.0.toDecimal())
                println("New time warp: $warp * $nudge = $totalWarp")
                clock.timeWarp.set(totalWarp)
            }
        }
    }

    companion object {
        private val scheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "Conductor Scheduler") }

        private const val TEMPO_NUDGE = 2.0

        private val byPlayer = WeakHashMap<ScorePlayer, Conductor>()

        fun forPlayer(player: ScorePlayer): Conductor = byPlayer.getOrPut(player) {
            val port = 57140 + Random.nextInt(10)
            val scorePane = player.context[PonticelloMainActivity].mainScoreView
            Conductor(player, scorePane, reactiveVariable(port))
        }
    }
}