package ponticello.model.player

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.live.QuantizationConfig
import ponticello.model.obj.AbstractRenamableObject
import ponticello.model.obj.playbackSettings
import ponticello.model.obj.withName
import ponticello.model.score.MeterObject
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.sc.client.SuperColliderClient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class ClockObject(
    val timeWarp: ReactiveVariable<Decimal> = reactiveVariable(1.0.asTime),
) : AbstractRenamableObject(), Runnable {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    private val lookAhead: Decimal get() = context.playbackSettings.lookAhead
    val period get() = PERIOD_S * timeWarp.now

    override val registry: ClockRegistry
        get() = context[ClockRegistry]

    @Transient
    private val activePlayers = mutableSetOf<ActivePlayer>()

    @Transient
    private val activeMeters = mutableSetOf<ActiveMeter>()

    @Transient
    private val scheduledActions = mutableListOf<ScheduledAction>()

    @Transient
    private var clockTime = 0.0.asTime

    @Transient
    private var runClock = false

    @Transient
    private lateinit var thread: Thread

    @Transient
    private lateinit var timeWarpObserver: Observer

    private val client get() = context[SuperColliderClient]

    override val canDelete: Boolean
        get() = name.now != "default"

    val activeMeter: ActiveMeter? get() = activeMeters.firstOrNull()

    override fun canRenameTo(newName: String): Boolean = name.now != "default" && super.canRenameTo(newName)

    override fun initialize(context: Context) {
        super.initialize(context)
        timeWarpObserver = timeWarp.observe { warp ->
            val scoreTime = activePlayers.firstOrNull()?.player?.currentTime
            val args =
                if (scoreTime != null) listOf(warp.toString(), scoreTime.toString())
                else listOf(warp.toString())
            client.sendAsync("set_time_warp", args)
        }
        if (timeWarp.now != one) {
            client.sendAsync("set_time_warp", listOf(timeWarp.now.toString()))
        }
        startClockThread()
    }

    private fun startClockThread() {
        thread = Thread(this)
        thread.isDaemon = true
        thread.priority = 7
        thread.name = "ClockThread ${name.now}"
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
            Logger.error("ClockThread ${name.now} crashed", e)
        }
        thread.start()
    }

    override fun run() {
        var lastTime = System.currentTimeMillis()
        while (!Thread.interrupted()) {
            val now = System.currentTimeMillis()
            val delta = (now - lastTime).toSeconds() * timeWarp.now
            lastTime = now
            if (!runClock) {
                trySleep()
                continue
            }
            synchronized(this) {
                processEvents(clockTime, delta)
                clockTime += delta
            }
            trySleep()
        }
    }

    private fun trySleep() {
        try {
            Thread.sleep(PERIOD_MS)
        } catch (e: InterruptedException) {
            return
        }
    }

    private fun processEvents(time: Decimal, delta: Decimal) {
        for ((player, startTime, offset) in activePlayers.toList()) { //copy to avoid concurrent modification
            if (startTime <= time && player.isScheduled.now && !player.isPlaying.now) {
                player.startPlaying()
            }
            if (!player.isPlaying.now) continue
            if (startTime + lookAhead <= time) {
                player.doCycle(time - startTime + offset, delta)
            }
        }
        val itr = scheduledActions.iterator()
        while (itr.hasNext()) {
            val (action, scheduledTime) = itr.next()
            if (time >= scheduledTime) {
                action.invoke(scheduledTime)
                itr.remove()
            }
        }
    }

    override val canRename: Boolean
        get() = name.now != "default"

    @Synchronized
    fun scheduleStart(player: ScorePlayer, quantization: QuantizationConfig?) {
        if (quantization != null && quantization.enableQuantization.now && quantization.meter.now.isResolved.now) {
            val offset = quantization.computeOffset()
            val meter = quantization.meter.now.force()
            val quant = quantization.computeQuant()
            scheduleStart(meter, quant, offset, player, action = null)
        } else {
            start(player)
        }
    }

    @Synchronized
    fun scheduleAction(quantization: QuantizationConfig, action: (delay: Decimal) -> Unit) {
        val meterRef = quantization.meter.now
        if (quantization.enableQuantization.now && meterRef.isResolved.now) {
            val offset = quantization.computeOffset()
            val meter = meterRef.force()
            val quant = quantization.computeQuant()
            scheduleStart(meter, quant, offset, player = null, action)
        } else {
            action(zero)
        }
    }

    private fun start(player: ScorePlayer) {
        val startTime = clockTime
        val offset = player.playHead.currentTime
        scheduleStart(player, {}, startTime, offset)
    }

    private fun scheduleStart(
        meter: MeterObject?, quant: Decimal, offset: Decimal,
        player: ScorePlayer?, action: ((delay: Decimal) -> Unit)? = null,
    ) {
        val activeMeter = activeMeters.firstOrNull { (m) -> m == meter }
        val playHeadPos = player?.playHead?.currentTime ?: 0.0.asTime
        val quantizationDelay = if (activeMeter != null) {
            val meterTime = clockTime - activeMeter.startTime
            var timeSinceLastMatch = meterTime - offset - playHeadPos
            timeSinceLastMatch %= quant
            if (timeSinceLastMatch < zero) timeSinceLastMatch += quant
            quant - timeSinceLastMatch
        } else zero
        val startTime = clockTime + quantizationDelay
        if (meter != null && player != null) {
            activeMeters.add(ActiveMeter(meter, player, startTime))
        }
        scheduleStart(player, action, startTime, playHeadPos)
    }

    private fun startClock() {
        clockTime = zero
        runClock = true
    }

    private fun scheduleStart(
        player: ScorePlayer?, action: ((Decimal) -> Unit)?,
        startTime: Decimal, offset: Decimal
    ) {
        if (player != null) {
            activePlayers.add(ActivePlayer(player, startTime, offset))
        }
        if (action != null) {
            scheduledActions.add(ScheduledAction(action, startTime))
        }
        if (!runClock) {
            startClock()
        }
    }

    @Synchronized
    fun stop(player: ScorePlayer) {
        activePlayers.removeIf { (p) -> p == player }
        activeMeters.removeIf { meter -> meter.player == player }
        if (activePlayers.isEmpty()) {
            stopClock()
        }
    }

    private fun stopClock() {
        clockTime = 0.0.asTime
        runClock = false
    }

    @Synchronized
    fun attach(player: ScorePlayer, meter: MeterObject, offset: Decimal) {
        val startTime = clockTime - offset
        activeMeters.add(ActiveMeter(meter, player, startTime))
        println("Attaching $meter to $player.")
    }

    @Synchronized
    fun detach(player: ScorePlayer, meter: MeterObject) {
        println("Detaching $meter from $player")
        activeMeters.removeIf { active -> active.meter == meter && active.player == player }
    }

    override fun dispose() {
        thread.interrupt()
    }

    private data class ActivePlayer(
        val player: ScorePlayer,
        val startTime: Decimal,
        val offset: Decimal
    )

    private data class ScheduledAction(
        val action: (Decimal) -> Unit,
        val scheduledTime: Decimal
    )

    data class ActiveMeter(
        val meter: MeterObject,
        val player: ScorePlayer,
        val startTime: Decimal,
    )

    companion object {
        private const val PERIOD_MS = 20L
        private val PERIOD_S = PERIOD_MS.toSeconds()

        fun withName(name: String) = ClockObject().withName(name)

        val TIME_WARP_SPEC = NumericalControlSpec(
            default = 1.0,
            min = 0.5, max = 2.0,
            step = 0.01.toDecimal(),
            warp = Warp.Exponential
        )
    }
}

