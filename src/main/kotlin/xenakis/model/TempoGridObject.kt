package xenakis.model

import hextant.core.editor.ListenerManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.copy
import xenakis.model.InteractionSettings.SnapOption
import xenakis.ui.TempoGridObjectView
import kotlin.math.roundToInt

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

    @Transient
    override val viewManager = ListenerManager.createWeakListenerManager<TempoGridObjectView>()

    private fun fireUpdatedConfig() {
        viewManager.notifyListeners { updatedConfig() }
    }

    override fun doClone(newName: String): ScoreObject =
        TempoGridObject(reactiveVariable(newName), beatsPerMinute.copy(), beatsPerBar.copy(), ticksPerBeat.copy())

    fun snapToGrid(instance: ScoreObjectInstance, t: Double, option: SnapOption): Double {
        val beatUnit = 60.0 / beatsPerMinute.now
        val unit = when (option) {
            SnapOption.Bars -> beatUnit * beatsPerBar.now
            SnapOption.Beats -> beatUnit
            SnapOption.Ticks -> beatUnit / ticksPerBeat.now
            else -> throw AssertionError("Invalid snap option $option")
        }
        return (((t - instance.start) / unit).roundToInt() * unit) + instance.start
    }

    fun getDuration(periodUnit: SnapOption): Double {
        val beatDur = (60.0 / beatsPerMinute.now)
        return when (periodUnit) {
            SnapOption.Seconds -> 1.0
            SnapOption.Bars -> beatsPerBar.now * beatDur
            SnapOption.Beats -> beatDur
            SnapOption.Ticks -> beatDur / ticksPerBeat.now
        }
    }

    override fun writeCode(
        name: String,
        position: ObjectPosition,
        env: ScorePlayEnv
    ): String = ""

    companion object {
        fun create(name: String, bpm: Int, bpb: Int, tpb: Int): TempoGridObject =
            TempoGridObject(reactiveVariable(name), reactiveVariable(bpm), reactiveVariable(bpb), reactiveVariable(tpb))

        fun createDefault(name: String) = create(name, 60, 4, 4)
    }
}