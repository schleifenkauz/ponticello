package ponticello.model.player

import ponticello.impl.Logger
import ponticello.impl.asTime
import ponticello.impl.div
import ponticello.impl.parseDecimal
import ponticello.model.score.TimeUnit
import ponticello.sc.client.OSCListener
import ponticello.sc.client.OSCReceiver
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveVariable

class Conductor(
    private val player: ScorePlayer,
    private val receiver: OSCReceiver = OSCReceiver.create(7773),
) : OSCListener {
    private var conductorTime = 0.0.asTime

    private val active = reactiveVariable(false)
    private var analysisProcess: Process? = null

    val isActive: ReactiveBoolean get() = active

    init {
        receiver.addListener(this)
    }

    fun startVideoAnalysis() {
        analysisProcess = ProcessBuilder("python", "live.py").start()
    }

    fun stopVideoAnalysis() {
        analysisProcess?.destroy()
        analysisProcess = null
    }

    override fun onMessage(path: String, id: Int, content: String) {
        val clock = player.getClock()
        val meter = clock.activeMeter ?: return
        when (path) {
            "started" -> active.now = true
            "exited" -> active.now = false
            "tempo" -> {
                val estimatedTempo = content.parseDecimal()
                if (estimatedTempo == null) {
                    Logger.error("Could not parse tempo: '$content'")
                    return
                }
                val scoreTempo = meter.beatsPerMinute.now
                clock.timeWarp.now = (estimatedTempo / scoreTempo)
            }

            "beat" -> {
                conductorTime += meter.getDuration(TimeUnit.Beats)
            }
        }
    }

    companion object
}