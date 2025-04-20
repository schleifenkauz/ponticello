package xenakis.model.player

import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.impl.asTime
import xenakis.impl.times
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.misc.PlayHead
import kotlin.concurrent.thread

abstract class AbstractPlayer(private val deltaT: Decimal, private val lookAhead: Decimal) : Thread() {
    private val _isPlaying = reactiveVariable(false)

    val isPlaying: ReactiveValue<Boolean> = _isPlaying

    abstract val loop: Boolean

    protected abstract val playHead: PlayHead

    val currentTime get() = playHead.currentTime

    protected abstract val client: SuperColliderClient

    protected abstract val maxTime: Decimal

    init {
        isDaemon = true
        start()
    }

    override fun run() {
        var lastTime = System.currentTimeMillis()
        while (!interrupted()) {
            val now = System.currentTimeMillis()
            if (isPlaying.now) {
                val dt = ((now - lastTime) / 1000.0).asTime
                var t = playHead.currentTime + lookAhead
                if (playHead.currentTime > maxTime) {
                    playHead.movePlayHeadToStart()
                    t = 0.0.asTime
                    if (!loop) {
                        pause()
                    }
                } else {
                    playHead.advance(dt)
                }
                scheduleEvents(t, dt)
            }
            lastTime = now
            try {
                sleep(toMs(deltaT))
            } catch (ex: InterruptedException) {
                //ex.printStackTrace()
                return
            }
        }
    }

    private fun toMs(t: Decimal) = (t * 1000).toLong()

    protected abstract fun startPlay(startFrom: Decimal): Boolean

    protected abstract fun scheduleEvents(t: Decimal, delta: Decimal)

    fun play(): Boolean {
        if (isPlaying.now) return true
        Logger.info("Starting playback", Logger.Category.Playback)
        if (!startPlay(playHead.currentTime)) {
            return false
        }
        thread(isDaemon = true) {
            sleep(toMs(lookAhead))
            _isPlaying.now = true
        }
        return true
    }

    fun pause() {
        if (!isPlaying.now) return
        _isPlaying.now = false
        pausePlayback()
    }

    fun reset() {
        pause()
        resetPlayback()
    }

    protected abstract fun pausePlayback()

    protected abstract fun resetPlayback()

    open fun close() {
        interrupt()
    }
}