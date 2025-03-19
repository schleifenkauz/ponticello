package xenakis.sc

import hextant.codegen.Choice
import hextant.context.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import xenakis.impl.*
import xenakis.sc.client.ScWriter
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

@Serializable(with = Warp.Serializer::class)
@Choice(initialValue = "Warp.Linear")
sealed class Warp : ScExpr {
    object Linear : Warp() {
        override fun toString(): String = "\\lin"

        override fun code(writer: ScWriter, context: Context) {
            writer.append("\\lin")
        }
    }

    object Exponential : Warp() {
        override fun toString(): String = "\\exp"
        override fun code(writer: ScWriter, context: Context) {
            writer.append("\\exp")
        }
    }

    data class Monomial(val exponent: Decimal) : Warp() {
        override fun toString(): String = "$exponent"

        override fun code(writer: ScWriter, context: Context) {
            writer.append(exponent.toString())
        }
    }

    companion object {
        val entries get() = listOf(Linear, Exponential, Monomial(2.0.toDecimal()), Monomial(3.0.toDecimal()))
    }

    object Serializer : KSerializer<Warp> {
        override val descriptor: SerialDescriptor
            get() = serialDescriptor<String>()

        override fun serialize(encoder: Encoder, value: Warp) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): Warp {
            val str = decoder.decodeString()
            return when {
                str == "\\lin" -> Linear
                str == "\\exp" -> Exponential
                str.startsWith("x^") -> Monomial(str.drop(2).parseDecimal()!!)
                else -> throw IllegalArgumentException("Invalid warp string $str")
            }
        }
    }
}

val Warp.map
    get(): (Double) -> Double = when (this) {
        Warp.Linear -> { x -> x }
        Warp.Exponential -> { x -> ln(x) }
        is Warp.Monomial -> { x -> x.pow(1 / exponent.toDouble()) }
    }

val Warp.unmap
    get(): (Double) -> Double = when (this) {
        Warp.Linear -> { x -> x }
        Warp.Exponential -> { x -> exp(x) }
        is Warp.Monomial -> { x -> x.pow(exponent.toDouble()) }
    }

interface Transformation {
    val sourceRange: DecimalRange
    val targetRange: DoubleRange

    fun map(value: Double): Double
    fun unmap(value: Double): Double
}

data class IdentityTransformation(val range: DoubleRange) : Transformation {
    override val sourceRange: DecimalRange
        get() = range.asDecimal

    override val targetRange: DoubleRange
        get() = range

    override fun map(value: Double): Double = value

    override fun unmap(value: Double): Double = value
}

data class LinearTransformation(
    override val sourceRange: DecimalRange,
    override val targetRange: DoubleRange
) : Transformation {
    private val factor = (targetRange.endInclusive - targetRange.start) / (sourceRange.endInclusive - targetRange.start)
    private val summand = targetRange.start - sourceRange.start

    override fun map(value: Double): Double = (value * factor + summand).value

    override fun unmap(value: Double): Double = (value / factor + summand).value
}

data class SpecTransformation(val spec: NumericalControlSpec, override val targetRange: DoubleRange) : Transformation {
    override val sourceRange: DecimalRange
        get() = spec.min.get()..spec.max.get()

    private val tdiff = targetRange.endInclusive - targetRange.start
    private val wmap = spec.warp.map
    private val wunmap = spec.warp.unmap
    private val fmin = wmap(spec.min.get().toDouble())
    private val fmax = wmap(spec.max.get().toDouble())
    private val fdiff = fmax - fmin

    override fun map(value: Double): Double = targetRange.start + (wmap(value) - fmin) * tdiff / fdiff

    override fun unmap(value: Double) = wunmap(((value - targetRange.start) * fdiff / tdiff) + fmin)
}
