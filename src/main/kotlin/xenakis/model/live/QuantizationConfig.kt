package xenakis.model.live

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.event.unitEvent
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.MeterReference
import xenakis.model.registry.MeterRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.score.ScoreObject
import xenakis.model.score.TimeUnit

@Serializable
class QuantizationConfig(
    val meter: ReactiveVariable<MeterReference> = reactiveVariable(ObjectReference.none()),
    val durationUnit: ReactiveVariable<TimeUnit> = reactiveVariable(TimeUnit.Seconds),
    val durationValue: ReactiveVariable<Decimal> = reactiveVariable(one),
    val quantizationUnit: ReactiveVariable<QuantizationUnit> = reactiveVariable(QuantizationUnit.Bars),
    val quantizationValue: ReactiveVariable<Int> = reactiveVariable(1),
    val offsetUnit: ReactiveVariable<TimeUnit> = reactiveVariable(TimeUnit.Seconds),
    val offsetValue: ReactiveVariable<Decimal> = reactiveVariable(zero),
    val enableQuantization: ReactiveVariable<Boolean> = reactiveVariable(true),
    val shiftGrid: ReactiveVariable<Boolean> = reactiveVariable(false),
) : AbstractContextualObject() {
    @Transient
    private lateinit var duration: ReactiveVariable<Decimal>

    fun duration(): ReactiveValue<Decimal> = duration

    @Transient
    private val update = unitEvent()

    val onUpdate get() = update.stream

    override fun initialize(context: Context) {
        super.initialize(context)
        meter.now.resolve(context[MeterRegistry])
        duration = reactiveVariable(computeDuration())
    }

    fun getQuantization(): Quantization =
        if (!enableQuantization.now) Quantization.None
        else Quantization.RelativeTo(
            meter.now,
            quantizationUnit.now, quantizationValue.now,
            offsetUnit.now, offsetValue.now
        )

    fun computeDuration(): Decimal {
        val grid = meter.now.get() ?: return zero
        val unit = grid.getDuration(durationUnit.now)
        return unit * durationValue.now
    }

    fun computeQuant(obj: ScoreObject): Decimal {
        val grid = meter.now.force()
        val unit = grid.getDuration(quantizationUnit.now, obj)
        return unit * quantizationValue.now
    }

    fun computeOffset(): Decimal {
        val grid = meter.now.force()
        val unit = grid.getDuration(offsetUnit.now)
        return unit * offsetValue.now
    }

    fun copy() = QuantizationConfig(
        meter.copy(),
        durationUnit.copy(), durationValue.copy(),
        quantizationUnit.copy(), quantizationValue.copy(),
        offsetUnit.copy(), offsetValue.copy(),
        enableQuantization.copy(), shiftGrid.copy()
    )

    fun update(source: QuantizationConfig) {
        meter.set(source.meter.now)
        durationUnit.set(source.durationUnit.now)
        durationValue.set(source.durationValue.now)
        quantizationUnit.set(source.quantizationUnit.now)
        quantizationValue.set(source.quantizationValue.now)
        offsetUnit.set(source.offsetUnit.now)
        offsetValue.set(source.offsetValue.now)
        enableQuantization.set(source.enableQuantization.now)
        shiftGrid.set(source.shiftGrid.now)
        duration.set(computeDuration())
        update.fire()
    }

    fun setDuration(value: Decimal) {
        val unit = meter.now.get()?.getDuration(durationUnit.now) ?: return
        durationValue.set(value / unit)
    }

    companion object {
        fun createDefault() = QuantizationConfig()
    }
}