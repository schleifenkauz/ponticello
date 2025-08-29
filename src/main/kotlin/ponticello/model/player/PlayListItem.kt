package ponticello.model.player

import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.impl.zero
import ponticello.model.live.QuantizationConfig
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.registry.NamedObject
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.ui.score.ScoreObjectView
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable

class PlayListItem(
    private val obj: ScoreObject,
    private val player: ScorePlayer,
    val quantization: QuantizationConfig,
    private val isLoop: ReactiveVariable<Boolean>,
    private val absoluteScoreY: ReactiveVariable<Decimal>
): AbstractContextualObject(), NamedObject {
    override val name: ReactiveValue<String>
        get() = obj.name
    override val isAdded: ReactiveBoolean
        get() = obj.isAdded

    override fun initialize(context: Context) {
        super.initialize(context)
        quantization.initialize(context)
    }

    fun inferQuantizationFrom(view: ScoreObjectView): Boolean {
        val position = view.instance.position
        val (gridStart, meter) = view.parentPane.getNearestGrid(position) ?: return false
        absoluteScoreY.set(view.absolutePosition.y)
        quantization.meter.set(meter.reference())
        val (durUnit, durValue) = meter.represent(obj.duration)
        quantization.durationUnit.set(durUnit)
        quantization.durationValue.set(durValue)
        var delta = view.absolutePosition.time - gridStart
        while (delta < zero) delta += obj.duration
        while (delta > obj.duration) delta -= obj.duration
        val (offsetUnit, offsetValue) = meter.represent(delta)
        quantization.offsetUnit.set(offsetUnit)
        quantization.offsetValue.set(offsetValue)
        return true
    }
}