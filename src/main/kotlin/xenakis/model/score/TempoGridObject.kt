package xenakis.model.score

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.flow.NodePlacement
import xenakis.ui.score.TempoGridObjectView

@Serializable
class TempoGridObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val beatsPerMinute: ReactiveVariable<Int>,
    val beatsPerBar: ReactiveVariable<Int>,
    val ticksPerBeat: ReactiveVariable<Int>,
    val firstBar: ReactiveVariable<Int> = reactiveVariable(0),
) : ScoreObject() {
    @Transient
    private val configObserver: Observer = beatsPerMinute.observe { _ -> fireUpdatedConfig() }
        .and(beatsPerBar.observe { _ -> fireUpdatedConfig() })
        .and(ticksPerBeat.observe { _ -> fireUpdatedConfig() })
        .and(firstBar.observe { _ -> fireUpdatedConfig() })

    override val type: String
        get() = "tempo"
    override val canMute: Boolean
        get() = false

    override val affectsPlayback: Boolean
        get() = false

    private fun fireUpdatedConfig() {
        notifyListeners<TempoGridObjectView> { updatedConfig() }
    }

    override fun doClone(newName: String): ScoreObject =
        TempoGridObject(
            reactiveVariable(newName),
            beatsPerMinute.copy(), beatsPerBar.copy(), ticksPerBeat.copy(), firstBar.copy()
        )

    private fun getBeatDur() = (60.0.asTime / beatsPerMinute.now)

    fun snapToGrid(t: Decimal, option: TimeUnit): Decimal {
        val beatDur = getBeatDur()
        val unit = when (option) {
            TimeUnit.Bars -> beatDur * beatsPerBar.now
            TimeUnit.Beats -> beatDur
            TimeUnit.Ticks -> beatDur / ticksPerBeat.now
            else -> throw AssertionError("Invalid snap option $option")
        }
        return (t / unit).roundToInt() * unit
    }

    fun getDuration(periodUnit: TimeUnit): Decimal {
        val beatDur = getBeatDur()
        return when (periodUnit) {
            TimeUnit.Seconds -> one
            TimeUnit.Bars -> beatsPerBar.now * beatDur
            TimeUnit.Beats -> beatDur
            TimeUnit.Ticks -> beatDur / ticksPerBeat.now
        }
    }

    fun represent(duration: Decimal): Pair<TimeUnit, Decimal> {
        val beatDur = getBeatDur()
        val durInBars = duration / (beatDur * beatsPerBar.now)
        if (durInBars.isInteger) return Pair(TimeUnit.Bars, durInBars)
        val durInBeats = duration / beatDur
        if (durInBeats.isInteger) return Pair(TimeUnit.Beats, durInBeats)
        val durInTicks = duration / (beatDur / ticksPerBeat.now)
        if (durInTicks.isInteger) return Pair(TimeUnit.Ticks, durInTicks)
        return Pair(TimeUnit.Seconds, duration)
    }

    override fun writeCode(
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
    ): String = ""

    companion object {
        fun create(name: String, bpm: Int, bpb: Int, tpb: Int): TempoGridObject =
            TempoGridObject(reactiveVariable(name), reactiveVariable(bpm), reactiveVariable(bpb), reactiveVariable(tpb))

        fun createDefault(name: String) = create(name, 60, 4, 4)
    }
}