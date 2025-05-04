package xenakis.model.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.live.QuantizationUnit
import xenakis.model.score.ScoreObject
import xenakis.model.score.TimeUnit

@Serializable
class MeterObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val beatsPerMinute: ReactiveVariable<Int>,
    val beatsPerBar: ReactiveVariable<Int>,
    val ticksPerBeat: ReactiveVariable<Int>,
) : AbstractRenamableObject() {
    override val canCopy: Boolean
        get() = true

    @Transient
    val clock = MeterClock()

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

    fun getDuration(unit: TimeUnit): Decimal {
        val beatDur = getBeatDur()
        return when (unit) {
            TimeUnit.Seconds -> one
            TimeUnit.Bars -> beatsPerBar.now * beatDur
            TimeUnit.Beats -> beatDur
            TimeUnit.Ticks -> beatDur / ticksPerBeat.now
        }
    }

    fun getDuration(unit: QuantizationUnit, obj: ScoreObject): Decimal {
        val beatDur = getBeatDur()
        return when (unit) {
            QuantizationUnit.Bars -> beatsPerBar.now * beatDur
            QuantizationUnit.Beats -> beatDur
            QuantizationUnit.Ticks -> beatDur / ticksPerBeat.now
            QuantizationUnit.ObjectDuration -> obj.duration
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

    override fun copy(name: String) = MeterObject(
        reactiveVariable(name),
        beatsPerMinute.copy(), beatsPerBar.copy(), ticksPerBeat.copy()
    )

    companion object {
        fun create(name: String, bpm: Int, bpb: Int, tpb: Int) = MeterObject(
            reactiveVariable(name),
            reactiveVariable(bpm),
            reactiveVariable(bpb),
            reactiveVariable(tpb)
        )
    }
}