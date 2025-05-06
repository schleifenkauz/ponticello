package xenakis.model.live

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
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
import xenakis.model.score.TimeUnit

@Serializable
class QuantizationConfig(
    val meter: ReactiveVariable<MeterReference> = reactiveVariable(ObjectReference.none()),
    val durationUnit: ReactiveVariable<TimeUnit> = reactiveVariable(TimeUnit.Seconds),
    val quantizationUnit: ReactiveVariable<QuantizationUnit> = reactiveVariable(QuantizationUnit.Bars),
    val quantizationValue: ReactiveVariable<Decimal> = reactiveVariable(one),
    val offsetUnit: ReactiveVariable<TimeUnit> = reactiveVariable(TimeUnit.Seconds),
    val offsetValue: ReactiveVariable<Decimal> = reactiveVariable(zero),
    val enableQuantization: ReactiveVariable<Boolean> = reactiveVariable(true),
    val shiftGrid: ReactiveVariable<Boolean> = reactiveVariable(false),
) : AbstractContextualObject() {
    @Transient
    val durationValue: ReactiveVariable<Decimal> = reactiveVariable(zero)

    @Transient
    private lateinit var duration: ReactiveVariable<Decimal>

    @Transient
    private val update = unitEvent()

    @Transient
    private lateinit var unitObserver: Observer

    val onUpdate get() = update.stream

    fun duration(): ReactiveValue<Decimal> = duration

    override fun initialize(context: Context) {
        super.initialize(context)
        meter.now.resolve(context[MeterRegistry])
        duration = reactiveVariable(computeDuration())
        automaticallyUpdateValuesOnUnitChange()
    }

    private fun automaticallyUpdateValuesOnUnitChange() {
        unitObserver = durationUnit.observe { _, oldUnit, newUnit ->
            updateValue(durationValue, oldUnit, newUnit)
        } and quantizationUnit.observe { _, oldUnit, newUnit ->
            updateValue(quantizationValue, oldUnit, newUnit)
        } and offsetUnit.observe { _, oldUnit, newUnit ->
            updateValue(durationValue, oldUnit, newUnit)
        }
    }

    private fun updateValue(value: ReactiveVariable<Decimal>, oldUnit: QuantizationUnit, newUnit: QuantizationUnit) {
        val meter = meter.now.get() ?: return
        val objDuration = computeDuration()
        val ratio = meter.getDuration(oldUnit, objDuration) / meter.getDuration(newUnit, objDuration)
        value.now *= ratio
        value.now = value.now.round(3)
    }

    private fun updateValue(value: ReactiveVariable<Decimal>, oldUnit: TimeUnit, newUnit: TimeUnit) {
        val meter = meter.now.get() ?: return
        val ratio = meter.getDuration(oldUnit) / meter.getDuration(newUnit)
        value.now *= ratio
        value.now = value.now.round(3)
    }

    fun getQuantization(): Quantization =
        if (!enableQuantization.now) Quantization.None
        else Quantization.RelativeTo(
            meter.now,
            quantizationUnit.now, quantizationValue.now,
            offsetUnit.now, offsetValue.now
        )

    fun computeDuration(): Decimal {
        val grid = meter.now.get() ?: return one
        val unit = grid.getDuration(durationUnit.now)
        return unit * durationValue.now
    }

    fun computeQuant(objDuration: Decimal): Decimal {
        val grid = meter.now.force()
        val unit = grid.getDuration(quantizationUnit.now, objDuration)
        return unit * quantizationValue.now
    }

    fun computeOffset(): Decimal {
        val grid = meter.now.force()
        val unit = grid.getDuration(offsetUnit.now)
        return unit * offsetValue.now
    }

    fun copy() = QuantizationConfig(
        meter.copy(),
        durationUnit.copy(),
        quantizationUnit.copy(), quantizationValue.copy(),
        offsetUnit.copy(), offsetValue.copy(),
        enableQuantization.copy(), shiftGrid.copy()
    ).also { copy -> copy.durationValue.set(durationValue.now) }

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
        val meter = meter.now.get()
        if (meter == null) {
            durationUnit.set(TimeUnit.Seconds)
            durationValue.set(value)
        } else {
            val unit = meter.getDuration(durationUnit.now)
            durationValue.set(value / unit)
        }
    }

    companion object {
        fun createDefault() = QuantizationConfig()
    }
}