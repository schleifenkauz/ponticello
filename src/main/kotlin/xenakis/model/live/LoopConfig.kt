package xenakis.model.live

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.impl.times
import xenakis.impl.zero
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.registry.NamedObjectList
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.TempoGridObject
import xenakis.model.score.TimeUnit

@Serializable
class LoopConfig(
    val grid: ReactiveVariable<ObjectReference<TempoGridObject>>,
    val durationUnit: ReactiveVariable<TimeUnit>, val durationValue: ReactiveVariable<Int>,
    val quantizationUnit: ReactiveVariable<QuantizationUnit>, val quantizationValue: ReactiveVariable<Int>,
    val offsetUnit: ReactiveVariable<TimeUnit>, val offsetValue: ReactiveVariable<Int>,
    val enableSnapping: ReactiveVariable<Boolean>,
    val enableQuantization: ReactiveVariable<Boolean>,
    val relativeToGridInstance: ReactiveVariable<Boolean>,
    val shiftGrid: ReactiveVariable<Boolean>,
) : AbstractContextualObject() {
    private lateinit var duration: ReactiveVariable<Decimal>

    fun duration(): ReactiveValue<Decimal> = duration

    override fun initialize(context: Context) {
        super.initialize(context)
        @Suppress("UNCHECKED_CAST")
        grid.now.resolve(context[ScoreObjectRegistry] as NamedObjectList<TempoGridObject>)
        duration = reactiveVariable(computeDuration())
    }

    fun getQuantization(): Quantization =
        if (!enableQuantization.now) Quantization.None
        else Quantization.RelativeTo(
            grid.now,
            quantizationUnit.now, quantizationValue.now,
            offsetUnit.now, offsetValue.now
        )

    fun computeDuration(): Decimal {
        val grid = grid.now.get() ?: return zero
        val unit = grid.getDuration(durationUnit.now)
        return unit * durationValue.now
    }

    fun copy() = LoopConfig(
        grid.copy(),
        durationUnit.copy(), durationValue.copy(),
        quantizationUnit.copy(), quantizationValue.copy(),
        offsetUnit.copy(), offsetValue.copy(),
        enableSnapping.copy(), enableQuantization.copy(),
        relativeToGridInstance.copy(), shiftGrid.copy()
    )

    fun update(source: LoopConfig) {
        grid.set(source.grid.now)
        durationUnit.set(source.durationUnit.now)
        durationValue.set(source.durationValue.now)
        quantizationUnit.set(source.quantizationUnit.now)
        quantizationValue.set(source.quantizationValue.now)
        offsetUnit.set(source.offsetUnit.now)
        offsetValue.set(source.offsetValue.now)
        enableSnapping.set(source.enableSnapping.now)
        enableQuantization.set(source.enableQuantization.now)
        relativeToGridInstance.set(source.relativeToGridInstance.now)
        shiftGrid.set(source.shiftGrid.now)
        duration.set(computeDuration())
    }

    companion object {
        fun createDefault() = LoopConfig(
            grid = reactiveVariable(ObjectReference.none()),
            durationUnit = reactiveVariable(TimeUnit.Seconds),
            durationValue = reactiveVariable(1),
            quantizationUnit = reactiveVariable(QuantizationUnit.Seconds),
            quantizationValue = reactiveVariable(1),
            offsetUnit = reactiveVariable(TimeUnit.Seconds),
            offsetValue = reactiveVariable(0),
            enableSnapping = reactiveVariable(true),
            enableQuantization = reactiveVariable(true),
            relativeToGridInstance = reactiveVariable(false),
            shiftGrid = reactiveVariable(false)
        )
    }
}