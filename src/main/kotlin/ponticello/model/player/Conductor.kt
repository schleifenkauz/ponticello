package ponticello.model.player

import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.messageselector.JavaRegexAddressMessageSelector
import com.illposed.osc.transport.OSCPortIn
import ponticello.impl.asTime
import ponticello.impl.div
import ponticello.impl.parseDecimal
import ponticello.model.score.TimeUnit
import ponticello.sc.client.getArgument
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveVariable

class Conductor(
    private val player: ScorePlayer,
    receiver: OSCPortIn,
) : OSCMessageListener {
    private var conductorTime = 0.0.asTime

    private val active = reactiveVariable(false)
    private var analysisProcess: Process? = null

    val isActive: ReactiveBoolean get() = active

    init {
        receiver.dispatcher.addListener(ALL_MESSAGES, this)
    }

    fun startVideoAnalysis() {
        analysisProcess = ProcessBuilder("python", "live.py").start()
    }

    fun stopVideoAnalysis() {
        analysisProcess?.destroy()
        analysisProcess = null
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        val clock = player.getClock()
        val meter = clock.activeMeter ?: return
        when (event.message.address) {
            "started" -> active.now = true
            "exited" -> active.now = false
            "tempo" -> {
                val estimatedTempo = event.message.getArgument<String>(1, "estimated tempo")?.parseDecimal() ?: return
                val scoreTempo = meter.beatsPerMinute.now
                clock.timeWarp.now = (estimatedTempo / scoreTempo)
            }

            "beat" -> {
                conductorTime += meter.getDuration(TimeUnit.Beats)
            }
        }
    }

    companion object {
        private val ALL_MESSAGES = JavaRegexAddressMessageSelector(".*")
    }
}