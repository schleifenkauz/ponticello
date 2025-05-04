package xenakis.model.live

import kotlinx.serialization.Serializable
import xenakis.impl.Decimal
import xenakis.model.registry.ObjectReference
import xenakis.model.score.TempoGridObject
import xenakis.model.score.TimeUnit

@Serializable
sealed interface Quantization {
    @Serializable
    data object None : Quantization

    @Serializable
    data class RelativeTo(
        val grid: ObjectReference<TempoGridObject>,
        val quantizationUnit: QuantizationUnit, val quantizationValue: Decimal,
        val offsetUnit: TimeUnit, val offset: Decimal,
    ) : Quantization

    @Serializable
    data class CustomRatio(val bpm: Int, val denominator: Int, val value: Int, val offset: Int) : Quantization

    @Serializable
    data class CustomGrid(
        val bpm: Int, val beatsPerBar: Int, val ticksPerBeat: Int,
        val unit: QuantizationUnit, val value: Int, val offset: Int,
    ) : Quantization

}