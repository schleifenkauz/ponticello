package xenakis.model.player

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.asTime
import xenakis.impl.times
import xenakis.impl.zero
import xenakis.model.Settings
import xenakis.model.live.QuantizationConfig
import xenakis.model.obj.AbstractRenamableObject
import xenakis.model.obj.MeterObject
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS

@Serializable
class ClockObject(override val mutableName: ReactiveVariable<String>) : AbstractRenamableObject() {
    @Transient
    private var lookAheadMs: Long = 0

    val period get() = PERIOD_S

    @Transient
    private val activePlayers = mutableSetOf<ActivePlayer>()

    @Transient
    private val activeMeters = mutableSetOf<ActiveMeter>()

    @Transient
    private var clockTask: Future<*>? = null

    @Transient
    private var clockStartTime = 0L

    @Transient
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ClockThread ${name.now}").apply {
            priority = 7
        }
    }

    override fun initialize(context: Context) {
        super.initialize(context)
        lookAheadMs = context[Settings].lookAhead.toMillis()
    }

    private fun runLoop() {
        val systemTime = System.currentTimeMillis()
        for ((player, startTime) in activePlayers.toList()) { //copy to avoid concurrent modification
            val elapsedTime = (systemTime - (clockStartTime + startTime)).toSeconds()
            player.doCycle(this, elapsedTime)
        }
    }

    override val canRename: Boolean
        get() = name.now != "default"

    fun scheduleStart(player: ScorePlayer, quantization: QuantizationConfig?) {
        if (quantization != null && quantization.enableQuantization.now && quantization.meter.now.isResolved.now) {
            val offset = quantization.computeOffset()
            val meter = quantization.meter.now.force()
            val quant = quantization.computeQuant(quantization.computeDuration())
            scheduleStart(meter, quant, offset, player)
        } else {
            start(player)
        }
    }

    fun scheduleAction(quantization: QuantizationConfig, objDuration: Decimal = zero, action: (Decimal) -> Unit) {
        val meterRef = quantization.meter.now
        if (quantization.enableQuantization.now && meterRef.isResolved.now) {
            val offset = quantization.computeOffset()
            val meter = meterRef.force()
            val quant = quantization.computeQuant(objDuration)
            scheduleStart(meter, quant, offset, player = null, action)
        } else {
            action(zero)
        }
    }

    private fun start(player: ScorePlayer) {
        val currentTime = getCurrentTime()
        val startTime = currentTime - player.playHead.currentTime.toMillis()
        scheduleStart(player, {}, startTime, quantizationDelay = 0L)
    }

    private fun scheduleStart(
        meter: MeterObject?, quant: Decimal, offset: Decimal,
        player: ScorePlayer?, action: (delay: Decimal) -> Unit = {},
    ) {
        val currentTime = getCurrentTime()
        val activeMeter = activeMeters.firstOrNull { (m) -> m == meter }
        val playHeadPos = player?.playHead?.currentTime?.toMillis() ?: 0
        val quantizationDelay = if (activeMeter != null) {
            val quantMillis = quant.toMillis()
            val meterTime = currentTime - activeMeter.startTime
            var timeSinceLastMatch = meterTime - offset.toMillis() - playHeadPos
            timeSinceLastMatch %= quantMillis
            if (timeSinceLastMatch < 0) timeSinceLastMatch += quantMillis
            quantMillis - timeSinceLastMatch
        } else 0L
        val startTime = currentTime + quantizationDelay - playHeadPos
        if (meter != null && player != null) {
            activeMeters.add(ActiveMeter(meter, player, startTime))
        }
        scheduleStart(player, action, startTime, quantizationDelay)
    }

    private fun scheduleStart(
        player: ScorePlayer?, action: (Decimal) -> Unit,
        startTime: Long, quantizationDelay: Long,
    ) {
        executor.schedule({
            action(quantizationDelay.toSeconds()); player?.startPlaying()
        }, quantizationDelay, MILLISECONDS)
        if (player != null) {
            executor.schedule(
                { activePlayers.add(ActivePlayer(player, startTime)) },
                quantizationDelay + lookAheadMs,
                MILLISECONDS
            )
        }
        if (clockTask == null) {
            clockTask = executor.scheduleAtFixedRate({ runLoop() }, 0, PERIOD_MS, MILLISECONDS)
        }
    }


    private fun getCurrentTime(): Long {
        val systemTime = System.currentTimeMillis()
        if (clockStartTime == 0L) {
            clockStartTime = systemTime
        }
        val currentTime = systemTime - clockStartTime
        return currentTime
    }

    fun stop(player: ScorePlayer) {
        activePlayers.removeIf { (p) -> p == player }
        activeMeters.removeIf { meter -> meter.player == player }
        if (activePlayers.isEmpty()) {
            clockStartTime = 0L
            clockTask?.cancel(true)
            clockTask = null
        }
    }

    fun attach(player: ScorePlayer, meter: MeterObject, offset: Decimal) {
        val startTime = System.currentTimeMillis() - clockStartTime - offset.toMillis()
        activeMeters.add(ActiveMeter(meter, player, startTime))
    }

    fun detach(player: ScorePlayer, meter: MeterObject) {
        activeMeters.removeIf { active -> active.meter == meter && active.player == player }
    }

    private data class ActivePlayer(
        val player: ScorePlayer,
        val startTime: Long,
    )

    private data class ActiveMeter(
        val meter: MeterObject,
        val player: ScorePlayer,
        val startTime: Long,
    )

    companion object {
        private const val PERIOD_MS = 20L
        private val PERIOD_S = PERIOD_MS.toSeconds()

        private fun Decimal.toMillis() = (this * 1000).toLong()

        private fun Long.toSeconds() = (this / 1000.0).asTime

        fun withName(name: String) = ClockObject(reactiveVariable(name))
    }
}