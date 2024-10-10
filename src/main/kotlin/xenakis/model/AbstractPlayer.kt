package xenakis.model

import xenakis.impl.Decimal
import xenakis.impl.SuperColliderClient
import xenakis.impl.asTime
import xenakis.impl.times
import xenakis.ui.PlayHead
import kotlin.concurrent.thread

abstract class AbstractPlayer(private val deltaT: Decimal) : Thread() {
    var isPlaying = false
        private set

    protected abstract val playHead: PlayHead

    val currentTime get() = playHead.currentTime

    protected abstract val client: SuperColliderClient

    protected abstract val lookAhead: Decimal

    init {
        isDaemon = true
        start()
    }

    override fun run() {
        var lastTime = System.currentTimeMillis()
        while (!interrupted()) {
            val now = System.currentTimeMillis()
            if (isPlaying) {
                val dt = ((now - lastTime) / 1000.0).asTime
                scheduleEvents(playHead.currentTime + lookAhead, dt)
                playHead.advance(dt)
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
        if (isPlaying) return true
        Logger.info("Starting playback", Logger.Category.Playback)
        if (!startPlay(playHead.currentTime)) {
            return false
        }
        thread(isDaemon = true) {
            sleep(toMs(lookAhead))
            isPlaying = true
        }
        return true
    }

    fun pause() {
        if (!isPlaying) return
        isPlaying = false
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