package xenakis.sc

import kotlinx.serialization.Serializable
import xenakis.impl.DoubleRange

@Serializable
sealed interface ControlSpec {
    val code: String
}

@Serializable
data class NumericalControlSpec(
    var default: Double,
    var min: Double,
    var max: Double,
    var warp: Warp,
    var step: Double,
) : ControlSpec {
    override val code: String
        get() = "kr($default, spec: [$min, $max, $warp, $step])"
}

@Serializable
data class BusControlSpec(val default: Bus) : ControlSpec {
    override val code: String
        get() = "kr(${default.code})"
}

@Serializable
data class BufferControlSpec(val default: Buffer) : ControlSpec {
    override val code: String
        get() = "kr(${default.code})"
}

fun NumericalControlSpec.mapOnto(targetRange: DoubleRange) = SpecTransformation(this, targetRange)