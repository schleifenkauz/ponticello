package ponticello.model.live

import fxutils.undo.UndoManager
import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.ClockReference
import ponticello.model.obj.MeterReference
import ponticello.model.player.ClockObject
import ponticello.model.registry.ClockRegistry
import ponticello.model.registry.MeterRegistry
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.model.score.TimeUnit
import ponticello.ui.live.QuantizationConfigEdit
import reaktive.Observer
import reaktive.and
import reaktive.event.unitEvent
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class QuantizationConfig(
    val meter: ReactiveVariable<MeterReference> = reactiveVariable(ObjectReference.none()),
    val clock: ReactiveVariable<ClockReference> = reactiveVariable(ObjectReference.none()),
    val durationUnit: ReactiveVariable<TimeUnit> = reactiveVariable(TimeUnit.Seconds),
    val quantizationUnit: ReactiveVariable<QuantizationUnit> = reactiveVariable(QuantizationUnit.Bars),
    val quantizationValue: ReactiveVariable<Decimal> = reactiveVariable(one),
    val offsetUnit: ReactiveVariable<TimeUnit> = reactiveVariable(TimeUnit.Seconds),
    val offsetValue: ReactiveVariable<Decimal> = reactiveVariable(zero),
    val enableQuantization: ReactiveVariable<Boolean> = reactiveVariable(false),
    val shiftGrid: ReactiveVariable<Boolean> = reactiveVariable(false),
) : AbstractContextualObject() {
    @Transient
    val durationValue: ReactiveVariable<Decimal> = reactiveVariable(zero)

    @Transient
    private lateinit var associatedObject: ScoreObject

    @Transient
    private val update = unitEvent()

    @Transient
    private lateinit var unitObserver: Observer

    val onUpdate get() = update.stream

    fun duration(): ReactiveValue<Decimal> = associatedObject.duration()

    override fun initialize(context: Context) {
        super.initialize(context)
        if (clock.now == ObjectReference.none<ClockObject>()) {
            clock.now = context[ClockRegistry].getDefault().reference()
        }
        clock.now.resolve(context[ClockRegistry])
        meter.now.resolve(context[MeterRegistry])
        automaticallyUpdateValuesOnUnitChange()
    }

    fun initialize(context: Context, associatedObject: ScoreObject) {
        initialize(context)
        this.associatedObject = associatedObject
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

    fun computeQuant(): Decimal {
        val grid = meter.now.force()
        val unit = grid.getDuration(quantizationUnit.now, duration().now)
        return unit * quantizationValue.now
    }

    fun computeOffset(): Decimal {
        val grid = meter.now.force()
        val unit = grid.getDuration(offsetUnit.now)
        return unit * offsetValue.now
    }

    fun copy() = QuantizationConfig(
        meter.copy(), clock.copy(),
        durationUnit.copy(),
        quantizationUnit.copy(), quantizationValue.copy(),
        offsetUnit.copy(), offsetValue.copy(),
        enableQuantization.copy(), shiftGrid.copy()
    ).also { copy -> copy.durationValue.set(durationValue.now) }

    fun update(source: QuantizationConfig) {
        val before = copy()
        meter.set(source.meter.now)
        clock.set(source.clock.now)
        durationUnit.set(source.durationUnit.now)
        durationValue.set(source.durationValue.now)
        quantizationUnit.set(source.quantizationUnit.now)
        quantizationValue.set(source.quantizationValue.now)
        offsetUnit.set(source.offsetUnit.now)
        offsetValue.set(source.offsetValue.now)
        enableQuantization.set(source.enableQuantization.now)
        shiftGrid.set(source.shiftGrid.now)
        update.fire()
        context[UndoManager].record(QuantizationConfigEdit(this, before, source))
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