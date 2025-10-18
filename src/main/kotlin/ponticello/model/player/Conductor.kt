package ponticello.model.player

import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.transport.OSCPortIn
import hextant.core.editor.ListenerManager
import ponticello.impl.*
import ponticello.model.obj.MeterObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.TempoGridObject
import ponticello.model.score.TimeUnit
import ponticello.sc.client.OSCSuperColliderClient
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

abstract class Conductor(
    protected val player: ScorePlayer,
    val options: ConductorOptions
) : OSCMessageListener {
    private val views = ListenerManager.createWeakListenerManager<View>()
    protected var conductorTime = 0.0.asTime
    protected var lastBeatMs = 0L
        private set
    private val beatTimes: Deque<Decimal> = LinkedList()
    protected var nBeats = 0
    private val active = reactiveVariable(false)
    private val scheduled = reactiveVariable(false)
    private var startingMeter: MeterObject? = null
    private var analysisProcess: Process? = null
    private var receiver: OSCPortIn? = null
    protected val clock get() = player.getClock()
    protected val activeMeter get() = clock.activeMeter?.meter ?: startingMeter
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

    protected abstract fun startVideoAnalysis(pythonExe: String, rubatoDir: File, startAt: Long): Process

    fun start(): Boolean {
        startingMeter = player.score.activeInstances(zero.rangeTo(one))
            .map(ScoreObjectInstance::obj)
            .filterIsInstance<TempoGridObject>()
            .firstOrNull()?.meter?.get() ?: return false

        scheduled.set(true)

        views.notifyListeners { onScheduled() }

        val receiver = OSCPortIn(options.port.now)
        receiver.startListening()
        receiver.dispatcher.addListener(OSCSuperColliderClient.ALL_MESSAGES, this)

        val rubatoDir = File(System.getProperty("user.home"), "dev/rubato")
        val pythonExe = rubatoDir.resolve(".venv/bin/python").absolutePath
        val startAt = System.currentTimeMillis() / 1000 + options.countdownTime.now
        analysisProcess = startVideoAnalysis(pythonExe, rubatoDir, startAt)
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
        clock.timeWarp.now = one
        views.notifyListeners { onStopped() }
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        if (event.message.address == "/started") {
            active.set(true)
            conductorTime = player.currentTime
            views.notifyListeners { onStarted() }
        }
        if (event.message.address == "/exited") {
            doStop()
        }
    }

    fun currentTempo(): Decimal {
        val deltas = beatTimes.zipWithNext { a, b -> b - a }
        val conductorTempo = deltas.size / deltas.fold(zero, Decimal::plus) * 60
        return conductorTempo
    }

    protected fun beat(timestamp: Decimal) {
        val meter = activeMeter ?: return
        lastBeatMs = System.currentTimeMillis()

        nBeats++
        if (nBeats > beatsPerBar + 1) {
            conductorTime += meter.getDuration(TimeUnit.Beats)
        }
        val barPosition = if (beatsPerBar != 0) ((nBeats - 1) % beatsPerBar) + 1 else 0
        views.notifyListeners { onBeat(barPosition, conductorTime) }

        beatTimes.addLast(timestamp)
        while (beatTimes.size > beatsPerBar) beatTimes.removeFirst()

        if (nBeats == beatsPerBar) {
            val conductorTempo = currentTempo()
            val beatDur = 60 / conductorTempo
            println("Beat duration: $beatDur")
            val delay = ((beatDur - player.lookAhead) * 1000).toLong()
            println("Scheduling the player with a delay of $delay ms.")
            scheduler.schedule(player::play, delay, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }


    interface View {
        fun onBeat(barPosition: Int, conductorTime: Decimal)
        fun onScheduled()
        fun onStopped()
        fun onStarted()
    }

    companion object {
        @JvmStatic
        protected val scheduler: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "Conductor Scheduler") }
    }
}