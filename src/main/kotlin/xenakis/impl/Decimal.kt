package xenakis.impl

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

@Serializable(with = Decimal.Serializer::class)
data class Decimal(val value: Double, val precision: Int) : Number(), Comparable<Decimal> {
    override fun toByte(): Byte = value.toInt().toByte()

    override fun toDouble(): Double = value

    override fun toFloat(): Float = value.toFloat()

    override fun toInt(): Int = value.toInt()

    override fun toLong(): Long = value.toLong()

    override fun toShort(): Short = value.toInt().toShort()

    override fun compareTo(other: Decimal): Int = value.compareTo(other.value)

    operator fun plus(other: Decimal): Decimal = Decimal(value + other.value, max(precision, other.precision))

    operator fun minus(other: Decimal): Decimal = Decimal(value - other.value, max(precision, other.precision))

    operator fun times(other: Decimal): Decimal = Decimal(value * other.value, max(precision, other.precision))

    operator fun div(other: Decimal): Decimal = Decimal(value / other.value, max(precision, other.precision))

    override fun toString(): String =
        if (precision == 0) value.toLong().toString()
        else String.format(Locale.US, "%.${precision}f", this)

    @OptIn(ExperimentalSerializationApi::class)
    object Serializer : KSerializer<Decimal> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Decimal {
            val str = when (decoder) {
                is JsonDecoder -> (decoder.decodeJsonElement().jsonPrimitive.content)
                else -> decoder.decodeString()
            }
            val precision = when (val decimalPointIdx = str.indexOf('.')) {
                -1 -> 0
                else -> str.length - decimalPointIdx - 1
            }
            val value = str.toDouble()
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