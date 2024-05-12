package xenakis.sc

import hextant.codegen.Choice
import kotlinx.serialization.Serializable
import xenakis.impl.DoubleRange
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

@Serializable
@Choice(defaultValue = "Warp.Linear")
sealed class Warp {
    @Serializable
    object Linear : Warp() {
        override fun toString(): String = "'lin'"
    }

    @Serializable
    object Exponential : Warp() {
        override fun toString(): String = "'exp'"
    }

    @Serializable
    data class Monomial(val exponent: Double) : Warp() {
        override fun toString(): String = "x^$exponent"
    }

    companion object {
        fun values() = arrayOf(Linear, Exponential, Monomial(2.0), Monomial(3.0))
    }
}

val Warp.map
    get(): (Double) -> Double = when (this) {
        Warp.Linear -> { x -> x }
        Warp.Exponential -> { x -> ln(x) }
        is Warp.Monomial -> { x -> x.pow(1 / exponent) }
    }

val Warp.unmap
    get(): (Double) -> Double = when (this) {
        Warp.Linear -> { x -> x }
        Warp.Exponential -> { x -> exp(x) }
        is Warp.Monomial -> { x -> x.pow(exponent) }
    }

interface Transformation {
    val sourceRange: DoubleRange
    val targetRange: DoubleRange

    fun map(value: Double): Double
    fun unmap(value: Double): Double
}

data class IdentityTransformation(val range: DoubleRange) : Transformation {
    override val sourceRange: DoubleRange
        get() = range

    override val targetRange: DoubleRange
        get() = range

    override fun map(value: Double): Double = value

    override fun unmap(value: Double): Double = value
}

data class LinearTransformation(
    override val sourceRange: DoubleRange,
    override val targetRange: DoubleRange
) : Transformation {
    private val factor = (targetRange.endInclusive - targetRange.start) / (sourceRange.endInclusive - targetRange.start)
    private val summand = targetRange.start - sourceRange.start

    override fun map(value: Double): Double = value * factor + summand

    override fun unmap(value: Double): Double = value / factor + summand
}

data class SpecTransformation(val spec: NumericalControlSpec, override val targetRange: DoubleRange) : Transformation {
    override val sourceRange: DoubleRange
        get() = spec.min.value..spec.max.value

    private val tdiff = targetRange.endInclusive - targetRange.start
    private val wmap = spec.warp.map
    private val wunmap = spec.warp.unmap
    private val fmin = wmap(spec.min.value)
    private val fmax = wmap(spec.max.value)
    private val fdiff = fmax - fmin

    override fun map(value: Double) = targetRange.start + (wmap(value) - fmin) * tdiff / fdiff

    override fun unmap(value: Double) = wunmap(((value - targetRange.start) * fdiff / tdiff) + fmin)
}
