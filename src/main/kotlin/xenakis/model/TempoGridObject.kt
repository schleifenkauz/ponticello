package xenakis.model

import hextant.core.editor.ListenerManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.copy
import xenakis.impl.getInt
import xenakis.model.InteractionSettings.SnapOption
import xenakis.ui.TempoGridObjectView
import kotlin.math.roundToInt

class TempoGridObject(
    name: String,
    val beatsPerMinute: ReactiveVariable<Int>,
    val beatsPerBar: ReactiveVariable<Int>,
    val ticksPerBeat: ReactiveVariable<Int>
) : RegularScoreObject(name) {
    private val configObserver: Observer

    override val type: String
        get() = "tempo"

    override val viewManager = ListenerManager.createWeakListenerManager<TempoGridObjectView>()

    init {
        configObserver = beatsPerMinute.observe { _ -> fireUpdatedConfig() }
            .and(beatsPerBar.observe { _ -> fireUpdatedConfig() })
            .and(ticksPerBeat.observe { _ -> fireUpdatedConfig() })
    }

    private fun fireUpdatedConfig() {
        viewManager.notifyListeners { updatedConfig() }
    }

    override fun copy(): ScoreObject =
        TempoGridObject(name.now, beatsPerMinute.copy(), beatsPerBar.copy(), ticksPerBeat.copy())

    fun snapToGrid(t: Double, option: SnapOption): Double {
        val beatUnit = 60.0 / beatsPerMinute.now
        val unit = when (option) {
            SnapOption.Bars -> beatUnit * beatsPerBar.now
            SnapOption.Beats -> beatUnit
            SnapOption.Ticks -> beatUnit / ticksPerBeat.now
            else -> throw AssertionError("Invalid snap option $option")
        }
        return (((t - start) / unit).roundToInt() * unit) + start
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

    override fun writeCode(env: ScorePlayEnv, name: String, playAt: Double) {}

    override fun JsonObjectBuilder.saveToJson() {
        put("bpm", beatsPerMinute.now)
        put("bpb", beatsPerBar.now)
        put("tpb", ticksPerBeat.now)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "tempo"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val bpm = getInt("bpm")!!
            val bpb = getInt("bpb")!!
            val tpb = getInt("tpb")!!
            return create(name, bpm, bpb, tpb)
        }
    }

    companion object {
        fun create(name: String, bpm: Int, bpb: Int, tpb: Int): TempoGridObject =
            TempoGridObject(name, reactiveVariable(bpm), reactiveVariable(bpb), reactiveVariable(tpb))

        fun createDefault(name: String) = create(name, 60, 4, 4)
    }
}