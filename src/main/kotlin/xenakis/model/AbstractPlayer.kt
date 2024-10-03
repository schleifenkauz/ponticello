package xenakis.model

import xenakis.impl.SuperColliderClient
import xenakis.ui.PlayHead
import kotlin.concurrent.thread

abstract class AbstractPlayer(private val deltaT: Double) : Thread() {
    var isPlaying = false
        private set

    protected abstract val playHead: PlayHead

    val currentTime get() = playHead.currentTime

    protected abstract val client: SuperColliderClient

    protected abstract val lookAhead: Double

    init {
        isDaemon = true
        start()
    }

    override fun run() {
        var lastTime = System.currentTimeMillis()
        while (!interrupted()) {
            val now = System.currentTimeMillis()
            if (isPlaying) {
                val dt = (now - lastTime) / 1000.0
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

    private fun toMs(t: Double) = (t * 1000).toLong()

    protected abstract fun startPlay(startFrom: Double)

    protected abstract fun scheduleEvents(t: Double, delta: Double)

    fun play() {
        if (isPlaying) return
        thread(isDaemon = true) {
            Logger.info("Starting playback", Logger.Category.Playback)
            startPlay(playHead.currentTime)
            sleep(toMs(lookAhead))
            isPlaying = true
        }
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

    protected open fun resetPlayback() {
        client.run("s.freeAll")
    }

    open fun close() {
        interrupt()
    }
}