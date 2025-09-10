package ponticello.model.player

import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.transport.OSCPortIn
import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.application.Platform
import ponticello.impl.*
import ponticello.model.obj.MeterObject
import ponticello.model.obj.project
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.TempoGridObject
import ponticello.model.score.TimeUnit
import ponticello.sc.client.OSCSuperColliderClient
import ponticello.sc.client.getArgument
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.tanh

class Conductor private constructor(
    val options: ConductorOptions,
    private val player: ScorePlayer
) : OSCMessageListener {
    private val views = ListenerManager.createWeakListenerManager<View>()

    private var conductorTime = 0.0.asTime

    private val beatTimes: Deque<Decimal> = LinkedList()
    private var nBeats = 0
    private val active = reactiveVariable(false)
    private val scheduled = reactiveVariable(false)
    private var startingMeter: MeterObject? = null
    private var analysisProcess: Process? = null
    private var receiver: OSCPortIn? = null

    private val clock get() = player.getClock()
    private val activeMeter get() = clock.activeMeter?.meter ?: startingMeter
    val beatsPerBar: Int
        get() = startingMeter?.beatsPerBar?.now ?: 0
    val isActive: ReactiveBoolean get() = active
    val isScheduled: ReactiveBoolean get() = scheduled

    private val playerStopObserver = player.isPlaying.observe { _, before, now ->
        if (before && !now) {
            stop()
        }
    }

    fun addView(view: View) {
        views.addListener(view)
    }

    fun start(): Boolean {
        startingMeter = player.score.activeInstances(zero..one)
            .map(ScoreObjectInstance::obj)
            .filterIsInstance<TempoGridObject>()
            .firstOrNull()?.meter?.get() ?: return false

        scheduled.set(true)

        views.notifyListeners { onScheduled() }

        val port = options.port.now
        val receiver = OSCPortIn(port)
        receiver.startListening()
        receiver.dispatcher.addListener(OSCSuperColliderClient.ALL_MESSAGES, this)

        val rubatoDir = File(System.getProperty("user.home"), "dev/rubato")
        val pythonExe = rubatoDir.resolve(".venv/bin/python").absolutePath
        val scriptPath = rubatoDir.resolve("live.py").absolutePath
        val startAt = System.currentTimeMillis() / 1000.0 + options.countdownTime.now
        val extraArguments = options.extraArguments.now.split(" ").toTypedArray()
        analysisProcess = ProcessBuilder()
            .command(
                pythonExe, scriptPath,
                "--udp-port", port.toString(),
                "--start-at", startAt.toString(),
                "--show-video",
                *extraArguments
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
        player.playHead.movePlayHeadToStart()
        views.notifyListeners { onStopped() }
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        if (event.message.address == "/started") {
            active.set(true)
            views.notifyListeners { onStarted() }
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
        val barPosition = if (beatsPerBar != 0) ((nBeats - 1) % beatsPerBar) + 1 else 0
        views.notifyListeners { onBeat(barPosition, conductorTime) }

        beatTimes.addLast(timestamp)
        while (beatTimes.size > beatsPerBar) beatTimes.removeFirst()
        val deltas = beatTimes.zipWithNext { a, b -> b - a }
        val conductorTempo = deltas.size / deltas.fold(zero, Decimal::plus) * 60

        if (nBeats == beatsPerBar) {
            val beatDur = 60 / conductorTempo
            println("Beat duration: $beatDur")
            val delay = ((beatDur - player.lookAhead) * 1000).toLong()
            println("Scheduling the player with a delay of $delay ms.")
            scheduler.schedule(player::play, delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            val warp = (conductorTempo / meter.beatsPerMinute.now).coerceIn(0.5.toDecimal(), 2.0.toDecimal())
            println("Time warp: $warp")
            clock.timeWarp.set(warp)
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
                clock.timeWarp.set(totalWarp)
            }
        }
    }

    interface View {
        fun onBeat(barPosition: Int, conductorTime: Decimal)
        fun onScheduled()
        fun onStopped()
        fun onStarted()
    }

    companion object {
        private val scheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "Conductor Scheduler") }

        private const val TEMPO_NUDGE = 2.0

        private val byPlayer = WeakHashMap<ScorePlayer, Conductor>()

        fun get(context: Context): Conductor {
            val player = context[ScorePlayer.MAIN]
            return byPlayer.getOrPut(player) {
                val options = context.project[PLAYBACK_SETTINGS].conductorOptions
                Conductor(options, player)
            }
        }
    }
}