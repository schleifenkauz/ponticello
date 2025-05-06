package xenakis.model.score

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.model.flow.NodePlacement
import xenakis.model.obj.MeterReference
import xenakis.model.registry.MeterRegistry
import xenakis.ui.score.TempoGridObjectView

@Serializable
class TempoGridObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
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

    override fun doClone(newName: String): ScoreObject =
        TempoGridObject(reactiveVariable(newName), meter, firstBar.copy())

    override fun writeCode(
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
        latency: Decimal,
    ): String = ""
}