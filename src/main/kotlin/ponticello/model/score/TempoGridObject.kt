package ponticello.model.score

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.model.obj.MeterReference
import ponticello.model.player.MeterRegistry
import ponticello.sc.client.ScWriter
import ponticello.ui.score.TempoGridObjectView
import reaktive.Observer
import reaktive.and
import reaktive.value.*

@Serializable
@SerialName("TempoGrid")
class TempoGridObject(
    val meter: MeterReference,
    val firstBar: ReactiveVariable<Int> = reactiveVariable(0),
) : ScoreObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    private lateinit var configObserver: Observer

    override val type: String
        get() = "tempo"
    override val canMute: Boolean
        get() = false
    override val affectsPlayback: Boolean
        get() = false
    override val canResizeVertically: Boolean
        get() = false
    override val canResizeHorizontally: Boolean
        get() = true

    val beatsPerMinute: ReactiveVariable<Decimal> get() = meter.force().beatsPerMinute
    val beatsPerBar: ReactiveVariable<Int> get() = meter.force().beatsPerBar
    val ticksPerBeat: ReactiveVariable<Int> get() = meter.force().ticksPerBeat

    override val associatedColor: ReactiveValue<Color?>
        get() = reactiveValue(Color.TRANSPARENT)

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

    override fun ScWriter.writeCode() = ""
}