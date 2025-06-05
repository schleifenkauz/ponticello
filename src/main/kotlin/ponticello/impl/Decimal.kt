package ponticello.impl

import fxutils.canonicalizeDecimal
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.util.*
import kotlin.math.max
import kotlin.math.pow

//TODO some of this can't be right
@Serializable(with = Decimal.Serializer::class)
class Decimal(val value: Double, val precision: Int) : Number(), Comparable<Decimal> {
    override fun toByte(): Byte = value.toInt().toByte()

    override fun toDouble(): Double = if (isNaN()) Double.NaN else value.round(precision)

    override fun toFloat(): Float = toDouble().toFloat()

    override fun toInt(): Int = value.toInt()

    override fun toLong(): Long = value.toLong()

    override fun toShort(): Short = value.toInt().toShort()

    override fun compareTo(other: Decimal): Int =
        if (this == other) 0 else value.compareTo(other.value)

    operator fun plus(other: Decimal): Decimal = Decimal(value + other.value, max(precision, other.precision))

    operator fun minus(other: Decimal): Decimal = Decimal(value - other.value, max(precision, other.precision))

    operator fun times(other: Decimal): Decimal = Decimal(value * other.value, max(precision, other.precision))

    operator fun div(other: Decimal): Decimal = Decimal(value / other.value, max(precision, other.precision))

    operator fun rem(other: Decimal): Decimal = Decimal(value % other.value, max(precision, other.precision))

    fun pow(exponent: Int): Decimal = Decimal(value.pow(exponent), precision)

    fun pow(exponent: Double): Decimal = Decimal(value.pow(exponent), precision)

    override fun toString(): String =
        when {
            isNaN() -> "NaN"
            value == Double.NEGATIVE_INFINITY -> "-inf"
            value == Double.POSITIVE_INFINITY -> "inf"
            precision == 0 -> value.toLong().toString()
            else -> value.format(precision)
        }

    fun toCanonicalString() = toString().canonicalizeDecimal()

    override fun equals(other: Any?): Boolean {
        if (other !is Decimal) return false
        val precision = maxOf(precision, other.precision)
        val str1 = value.format(precision)
        val str2 = other.value.format(precision)
        return str1 == str2
    }

    override fun hashCode(): Int = toCanonicalString().hashCode()

    fun isNaN() = value.isNaN()

    companion object {
        val NaN get() = Decimal(Double.NaN, 0)

        val INF get() = Decimal(Double.POSITIVE_INFINITY, 0)
        val NINF get() = Decimal(Double.NEGATIVE_INFINITY, 0)

        private fun Double.format(precision: Int): String {
            val str = String.format(Locale.US, "%.${precision}f", this)
            return if (str.all { ch -> ch in setOf('0', '.', '-') }) str.removePrefix("-")
            else str
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    object Serializer : KSerializer<Decimal> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("Decimal", PrimitiveKind.DOUBLE)

        override fun deserialize(decoder: Decoder): Decimal {
            val str = when (decoder) {
                is JsonDecoder -> (decoder.decodeJsonElement().jsonPrimitive.content)
                else -> decoder.decodeString()
            }
            val precision = when (val decimalPointIdx = str.indexOf('.')) {
                -1 -> 0
                else -> str.length - decimalPointIdx - 1
            }
            if (str == "inf") return INF
            val value = str.toDoubleOrNull() ?: error("Invalid decimal: $str")
            return Decimal(value, precision)
        }

        override fun serialize(encoder: Encoder, value: Decimal) {
            val str = value.toString()
            when (encoder) {
                is JsonEncoder -> encoder.encodeJsonElement(JsonUnquotedLiteral(str))
                else -> encoder.encodeString(str)
            }
        }
    }
}