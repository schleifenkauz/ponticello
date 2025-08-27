package ponticello.model.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.*
import ponticello.model.live.QuantizationUnit
import ponticello.model.score.TimeUnit
import reaktive.Observer
import reaktive.Reactive
import reaktive.and
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class MeterObject(
    val beatsPerMinute: ReactiveVariable<Int>,
    val beatsPerBar: ReactiveVariable<Int>,
    val ticksPerBeat: ReactiveVariable<Int>,
) : AbstractRenamableObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override val canCopy: Boolean
        get() = true

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

    fun getDuration(unit: QuantizationUnit, objDuration: Decimal): Decimal {
        val beatDur = getBeatDur()
        return when (unit) {
            QuantizationUnit.Bars -> beatsPerBar.now * beatDur
            QuantizationUnit.Beats -> beatDur
            QuantizationUnit.Ticks -> beatDur / ticksPerBeat.now
            QuantizationUnit.ObjectDuration -> objDuration
        }
    }

    fun getDuration(bar: Int, beat: Int, tick: Int): Decimal {
        val secondsPerBeat = 60.0.asTime / beatsPerMinute.now
        return ((bar * beatsPerBar.now) + beat + (tick.toDouble() / ticksPerBeat.now)) * secondsPerBeat
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

    override fun copy() = MeterObject(
        beatsPerMinute.copy(), beatsPerBar.copy(), ticksPerBeat.copy()
    )

    fun observe(handler: (Reactive) -> Unit): Observer =
        beatsPerMinute.observe(handler) and beatsPerBar.observe(handler) and ticksPerBeat.observe(handler)

    companion object {
        fun create(bpm: Int, bpb: Int, tpb: Int) = MeterObject(
            reactiveVariable(bpm),
            reactiveVariable(bpb),
            reactiveVariable(tpb)
        )
    }
}