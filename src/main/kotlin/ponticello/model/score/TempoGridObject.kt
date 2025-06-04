package ponticello.model.score

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.MeterReference
import ponticello.model.obj.ParameterDefObject
import ponticello.model.registry.MeterRegistry
import ponticello.model.score.controls.ParameterControl
import ponticello.ui.score.TempoGridObjectView
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class TempoGridObject(
    val meter: MeterReference,
    val firstBar: ReactiveVariable<Int> = reactiveVariable(0),
) : ScoreObject() {
    @Transient
    private lateinit var configObserver: Observer

    override val type: String
        get() = "tempo"
    override val canMute: Boolean
        get() = false
    override val affectsPlayback: Boolean
        get() = false

    val beatsPerMinute: ReactiveVariable<Int> get() = meter.force().beatsPerMinute
    val beatsPerBar: ReactiveVariable<Int> get() = meter.force().beatsPerBar
    val ticksPerBeat: ReactiveVariable<Int> get() = meter.force().ticksPerBeat

    override fun initialize(context: Context) {
        super.initialize(context)
        meter.resolve(context[MeterRegistry])
        if (meter.isResolved.now) {
            configObserver = beatsPerMinute.observe { _ -> fireUpdatedConfig() }
                .and(beatsPerBar.observe { _ -> fireUpdatedConfig() })
                .and(ticksPerBeat.observe { _ -> fireUpdatedConfig() })
                .and(firstBar.observe { _ -> fireUpdatedConfig() })
        }
    }

    private fun fireUpdatedConfig() {
        notifyListeners<TempoGridObjectView> { updatedConfig() }
    }

    override fun doClone(): ScoreObject =
        TempoGridObject(meter, firstBar.copy())

    override fun writeCode(
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
        latency: Decimal,
        extraArguments: Map<ParameterDefObject, ParameterControl>,
    ): String = ""
}