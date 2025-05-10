package ponticello.model.live

import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.model.obj.MeterReference
import ponticello.model.score.TimeUnit

@Serializable
sealed interface Quantization {
    @Serializable
    data object None : Quantization

    @Serializable
    data class RelativeTo(
        val grid: MeterReference,
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