package ponticello.model.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.Settings
import ponticello.model.live.QuantizationConfig
import ponticello.model.obj.AbstractRenamableObject
import ponticello.model.obj.MeterObject
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS

@Serializable
class ClockObject(
    override val mutableName: ReactiveVariable<String>,
    val timeWarp: ReactiveVariable<Decimal> = reactiveVariable(1.0.asTime),
) : AbstractRenamableObject() {
    private val lookAhead: Decimal get() = context[Settings].lookAhead

    val period get() = PERIOD_S

    @Transient
    private val activePlayers = mutableSetOf<ActivePlayer>()

    @Transient
    private val activeMeters = mutableSetOf<ActiveMeter>()

    @Transient
    private var clockTask: Future<*>? = null

    @Transient
    private var clockTime = 0.0.asTime

    @Transient
    private var lastSystemTime = 0L

    @Transient
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ClockThread ${name.now}").apply {
            priority = 7
        }
    }

    private fun runLoop() {
        val now = System.currentTimeMillis()
        clockTime += (now - lastSystemTime).toSeconds() * timeWarp.now
        lastSystemTime = now
        for ((player, startTime) in activePlayers.toList()) { //copy to avoid concurrent modification
            player.doCycle(this, clockTime - startTime)
        }
    }

    override val canRename: Boolean
        get() = name.now != "default"

    fun scheduleStart(player: ScorePlayer, quantization: QuantizationConfig?) {
        if (quantization != null && quantization.enableQuantization.now && quantization.meter.now.isResolved.now) {
            val offset = quantization.computeOffset()
            val meter = quantization.meter.now.force()
            val quant = quantization.computeQuant()
            scheduleStart(meter, quant, offset, player)
        } else {
            start(player)
        }
    }

    fun scheduleAction(quantization: QuantizationConfig, action: (Decimal) -> Unit) {
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
        val currentTime = getCurrentTime()
        val startTime = currentTime - player.playHead.currentTime
        scheduleStart(player, {}, startTime, quantizationDelay = zero)
    }

    private fun scheduleStart(
        meter: MeterObject?, quant: Decimal, offset: Decimal,
        player: ScorePlayer?, action: (delay: Decimal) -> Unit = {},
    ) {
        val currentTime = clockTime
        val activeMeter = activeMeters.firstOrNull { (m) -> m == meter }
        val playHeadPos = player?.playHead?.currentTime ?: 0.0.asTime
        val quantizationDelay = if (activeMeter != null) {
            val meterTime = currentTime - activeMeter.startTime
            var timeSinceLastMatch = meterTime - offset - playHeadPos
            timeSinceLastMatch %= quant
            if (timeSinceLastMatch < zero) timeSinceLastMatch += quant
            quant - timeSinceLastMatch
        } else zero
        val startTime = currentTime + quantizationDelay - playHeadPos
        if (meter != null && player != null) {
            activeMeters.add(ActiveMeter(meter, player, startTime))
        }
        scheduleStart(player, action, startTime, quantizationDelay)
    }

    private fun scheduleStart(
        player: ScorePlayer?, action: (Decimal) -> Unit,
        startTime: Decimal, quantizationDelay: Decimal,
    ) {
        executor.schedule({
            action(quantizationDelay); player?.startPlaying()
        }, quantizationDelay.toMillis(), MILLISECONDS)
        if (player != null) {
            executor.schedule(
                { activePlayers.add(ActivePlayer(player, startTime)) },
                (quantizationDelay + lookAhead).toMillis(),
                MILLISECONDS
            )
        }
        if (clockTask == null) {
            lastSystemTime = System.currentTimeMillis()
            clockTask = executor.scheduleAtFixedRate({ runLoop() }, 0, PERIOD_MS, MILLISECONDS)
        }
    }


    private fun getCurrentTime(): Decimal = clockTime

    fun stop(player: ScorePlayer) {
        activePlayers.removeIf { (p) -> p == player }
        activeMeters.removeIf { meter -> meter.player == player }
        if (activePlayers.isEmpty()) {
            clockTime = 0.0.asTime
            clockTask?.cancel(true)
            clockTask = null
        }
    }

    fun attach(player: ScorePlayer, meter: MeterObject, offset: Decimal) {
        val startTime = clockTime - offset
        activeMeters.add(ActiveMeter(meter, player, startTime))
    }

    fun detach(player: ScorePlayer, meter: MeterObject) {
        activeMeters.removeIf { active -> active.meter == meter && active.player == player }
    }

    private data class ActivePlayer(
        val player: ScorePlayer,
        val startTime: Decimal,
    )

    private data class ActiveMeter(
        val meter: MeterObject,
        val player: ScorePlayer,
        val startTime: Decimal,
    )

    companion object {
        private const val PERIOD_MS = 20L
        private val PERIOD_S = PERIOD_MS.toSeconds()

        private fun Decimal.toMillis() = (this * 1000).toLong()

        private fun Long.toSeconds() = (this / 1000.0).asTime

        fun withName(name: String) = ClockObject(reactiveVariable(name))

        val TIME_WARP_SPEC = NumericalControlSpec(
            default = 1.0,
            min = 0.5, max = 2.0,
            step = 0.05.toDecimal(),
            lag = 0.01,
            warp = Warp.Exponential
        )
    }
}