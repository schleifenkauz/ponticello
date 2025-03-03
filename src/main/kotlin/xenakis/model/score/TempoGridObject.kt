package xenakis.model.score

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.InteractionSettings.SnapOption
import xenakis.model.flow.ScoreObjectInfo
import xenakis.ui.score.TempoGridObjectView

@Serializable
class TempoGridObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val beatsPerMinute: ReactiveVariable<Int>,
    val beatsPerBar: ReactiveVariable<Int>,
    val ticksPerBeat: ReactiveVariable<Int>
) : ScoreObject() {
    @Transient
    private val configObserver: Observer = beatsPerMinute.observe { _ -> fireUpdatedConfig() }
        .and(beatsPerBar.observe { _ -> fireUpdatedConfig() })
        .and(ticksPerBeat.observe { _ -> fireUpdatedConfig() })

    override val type: String
        get() = "tempo"
    override val canMute: Boolean
        get() = false

    private fun fireUpdatedConfig() {
        notifyListeners<TempoGridObjectView> { updatedConfig() }
    }

    override fun doClone(newName: String): ScoreObject =
        TempoGridObject(reactiveVariable(newName), beatsPerMinute.copy(), beatsPerBar.copy(), ticksPerBeat.copy())

    fun snapToGrid(t: Decimal, option: SnapOption): Decimal {
        val beatUnit = 60.0.asTime / beatsPerMinute.now
        val unit = when (option) {
            SnapOption.Bars -> beatUnit * beatsPerBar.now
            SnapOption.Beats -> beatUnit
            SnapOption.Ticks -> beatUnit / ticksPerBeat.now
            else -> throw AssertionError("Invalid snap option $option")
        }
        return (t / unit).roundToInt() * unit
    }

    fun getDuration(periodUnit: SnapOption): Decimal {
        val beatDur = (60.0.asTime / beatsPerMinute.now)
        return when (periodUnit) {
            SnapOption.Seconds -> one
            SnapOption.Bars -> beatsPerBar.now * beatDur
            SnapOption.Beats -> beatDur
            SnapOption.Ticks -> beatDur / ticksPerBeat.now
        }
    }

    override fun writeCode(
        info: ScoreObjectInfo
    ): String = ""

    companion object {
        fun create(name: String, bpm: Int, bpb: Int, tpb: Int): TempoGridObject =
            TempoGridObject(reactiveVariable(name), reactiveVariable(bpm), reactiveVariable(bpb), reactiveVariable(tpb))

        fun createDefault(name: String) = create(name, 60, 4, 4)
    }
}