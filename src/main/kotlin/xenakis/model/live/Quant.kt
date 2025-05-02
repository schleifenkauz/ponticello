package xenakis.model.live

import kotlinx.serialization.Serializable
import xenakis.model.registry.ObjectReference
import xenakis.model.score.TempoGridObject

@Serializable
sealed interface Quant {
    @Serializable
    data object None : Quant

    @Serializable
    data class RelativeTo(
        val grid: ObjectReference<TempoGridObject>,
        val unit: QuantUnit, val value: Int, val offset: Int,
    ) : Quant

    @Serializable
    data class CustomRatio(val bpm: Int, val denominator: Int, val value: Int, val offset: Int) : Quant

    @Serializable
    data class CustomGrid(
        val bpm: Int, val beatsPerBar: Int, val ticksPerBeat: Int,
        val unit: QuantUnit, val value: Int, val offset: Int,
    ) : Quant

    enum class QuantUnit {
        Bars, Beats, Ticks, Seconds, ObjectDuration
    }
}