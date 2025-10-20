package ponticello.model.obj

import fxutils.undo.AbstractEdit
import fxutils.undo.UndoManager
import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.live.QuantizationUnit
import ponticello.model.score.TimeUnit
import ponticello.sc.client.ScWriter
import reaktive.Observer
import reaktive.Reactive
import reaktive.and
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class MeterObject private constructor(
    val beatsPerMinute: ReactiveVariable<Decimal> = reactiveVariable(zero),
    val beatsPerBar: ReactiveVariable<Int> = reactiveVariable(0),
    val ticksPerBeat: ReactiveVariable<Int> = reactiveVariable(0),
) : AbstractSuperColliderObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    fun isNone() = beatsPerMinute.now == zero

    @Transient
    private lateinit var syncObserver: Observer

    override val superColliderName: String
        get() = "~meter_${name.now}"

    private val bpmProperty get() = "$superColliderName[\\bpm]"
    private val bpbProperty get() = "$superColliderName[\\bpb]"
    private val tpbProperty get() = "$superColliderName[\\tpb]"

    override fun ScWriter.createObject() {
        +"$superColliderName = ()"
        sync()
    }

    override fun ScWriter.sync() {
        +"$bpmProperty = ${beatsPerMinute.now}"
        +"$bpbProperty = ${beatsPerBar.now}"
        +"$tpbProperty = ${ticksPerBeat.now}"
    }

    override fun ScWriter.freeObject() {
        +"if ($superColliderName != nil) { $superColliderName = nil }"
    }

    override fun initialize(context: Context) {
        super.initialize(context)
        syncObserver = beatsPerMinute.observe { _, _, new ->
            client.run("$bpmProperty = $new")
        } and beatsPerBar.observe { _, _, new ->
            client.run("$bpbProperty = $new")
        } and ticksPerBeat.observe { _, _, new ->
            client.run("$tpbProperty = $new")
        }
    }

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

    fun getDuration(unit: QuantizationUnit): Decimal {
        val beatDur = getBeatDur()
        return when (unit) {
            QuantizationUnit.Bars -> beatsPerBar.now * beatDur
            QuantizationUnit.Beats -> beatDur
            QuantizationUnit.Ticks -> beatDur / ticksPerBeat.now
        }
    }

    fun getDuration(bar: Int, beat: Int, tick: Int): Decimal {
        val secondsPerBeat = 60.0.asTime / beatsPerMinute.now
        return ((bar * beatsPerBar.now) + beat + (tick.toDouble() / ticksPerBeat.now)) * secondsPerBeat
    }

    fun represent(duration: Decimal): Pair<TimeUnit, Decimal> {
        if (duration == zero) return Pair(TimeUnit.Ticks, zero)
        val beatDur = getBeatDur()
        val durInBars = duration / (beatDur * beatsPerBar.now)
        if (durInBars.isInteger) return Pair(TimeUnit.Bars, durInBars)
        val durInBeats = duration / beatDur
        if (durInBeats.isInteger) return Pair(TimeUnit.Beats, durInBeats)
        val durInTicks = duration / (beatDur / ticksPerBeat.now)
        if (durInTicks.isInteger) return Pair(TimeUnit.Ticks, durInTicks)
        return Pair(TimeUnit.Seconds, duration)
    }

    fun update(source: MeterObject, undoManager: UndoManager) {
        if (source === this) return
        val bpmBefore = beatsPerMinute.now
        val bpbBefore = beatsPerBar.now
        val tpbBefore = ticksPerBeat.now

        val bpmAfter = source.beatsPerMinute.now
        val bpbAfter = source.beatsPerBar.now
        val tpbAfter = source.ticksPerBeat.now

        if (bpmBefore == bpmAfter && bpbBefore == bpbAfter && tpbBefore == tpbAfter) return

        beatsPerMinute.now = bpmAfter
        beatsPerBar.now = bpbAfter
        ticksPerBeat.now = tpbAfter
        undoManager.record(
            UpdateMeterEdit(
                this,
                bpmBefore, bpbBefore, tpbBefore,
                bpmAfter, bpbAfter, source.ticksPerBeat.now,
            )
        )
    }

    override fun copy() = MeterObject(
        beatsPerMinute.copy(), beatsPerBar.copy(), ticksPerBeat.copy()
    )

    fun observe(handler: (Reactive) -> Unit): Observer =
        beatsPerMinute.observe(handler) and beatsPerBar.observe(handler) and ticksPerBeat.observe(handler)

    override fun toString(): String = "#${name.now} (${beatsPerMinute.now}bpm, ${beatsPerBar.now}x${ticksPerBeat.now})"

    private class UpdateMeterEdit(
        private val meter: MeterObject,
        private val bpmBefore: Decimal,
        private val bpbBefore: Int,
        private val tpbBefore: Int,
        private val bpmAfter: Decimal,
        private val bpbAfter: Int,
        private val tpbAfter: Int,
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Update meter"

        override fun doRedo() {
            meter.beatsPerMinute.now = bpmAfter
            meter.beatsPerBar.now = bpbAfter
            meter.ticksPerBeat.now = tpbAfter
        }

        override fun doUndo() {
            meter.beatsPerMinute.now = bpmBefore
            meter.beatsPerBar.now = bpbBefore
            meter.ticksPerBeat.now = tpbBefore
        }
    }

    companion object {
        fun create(bpm: Decimal, bpb: Int, tpb: Int) = MeterObject(
            reactiveVariable(bpm),
            reactiveVariable(bpb),
            reactiveVariable(tpb)
        )

        fun createDefault(bpm: Decimal) = create(bpm, 4, 4)

        fun createDefault() = create(60.toDecimal(), 4, 4)

        fun none() = MeterObject()
    }
}