package ponticello.impl

import kotlinx.serialization.Serializable
import ponticello.model.score.ObjectPosition
import kotlin.math.*

typealias DoubleRange = ClosedFloatingPointRange<Double>

val DoubleRange.asDecimal: DecimalRange get() = start.toDecimal()..endInclusive.toDecimal()

@Serializable
data class DecimalRange(
    override val start: Decimal, override val endInclusive: Decimal,
) : ClosedFloatingPointRange<Decimal> {
    override fun lessThanOrEquals(a: Decimal, b: Decimal): Boolean = a <= b

    override fun toString(): String = "$start..$endInclusive"
}

operator fun Decimal.rangeTo(other: Decimal) = DecimalRange(this, other)

infix fun DecimalRange.step(step: Double) = sequence {
    var value = start
    while (value <= endInclusive) {
        yield(value)
        value += step
    }
}

operator fun DecimalRange.plus(delta: Decimal) = DecimalRange(start + delta, endInclusive + delta)

operator fun DecimalRange.minus(delta: Decimal) = DecimalRange(start - delta, endInclusive - delta)

val DecimalRange.dur get() = endInclusive - start

fun Double.withPrecision(precision: Int) = Decimal(this, precision)

val Double.asTime get() = withPrecision(ObjectPosition.TIME_PRECISION)
val Double.asY get() = withPrecision(ObjectPosition.Y_PRECISION)

fun zero(precision: Int) = Decimal(0.0, precision)

val zero = Decimal(0.0, 0)

fun one(precision: Int) = Decimal(1.0, precision)

val one = Decimal(1.0, 0)

operator fun Decimal.plus(other: Double) = Decimal(value + other, precision)

operator fun Decimal.minus(other: Double) = Decimal(value - other, precision)

operator fun Decimal.times(other: Double) = Decimal(value * other, precision)

operator fun Decimal.div(other: Double) = Decimal(value / other, precision)

operator fun Decimal.plus(other: Int) = Decimal(value + other, precision)

operator fun Decimal.minus(other: Int) = Decimal(value - other, precision)

operator fun Decimal.times(other: Int) = Decimal(value * other, precision)

operator fun Decimal.div(other: Int) = Decimal(value / other, precision)

infix fun Decimal.mod(other: Decimal): Decimal {
    val rem = this.rem(other)
    return if (rem < zero) rem + other else rem
}

infix fun Decimal.exp(exponent: Int) = Decimal(value.pow(exponent), precision)

operator fun Double.plus(other: Decimal) = Decimal(this + other.value, other.precision)

operator fun Double.minus(other: Decimal) = Decimal(this - other.value, other.precision)

operator fun Double.times(other: Decimal) = Decimal(this * other.value, other.precision)

operator fun Double.div(other: Decimal) = Decimal(this / other.value, other.precision)

operator fun Int.plus(other: Decimal) = Decimal(this + other.value, other.precision)

operator fun Int.minus(other: Decimal) = Decimal(this - other.value, other.precision)

operator fun Int.times(other: Decimal) = Decimal(this * other.value, other.precision)

operator fun Int.div(other: Decimal) = Decimal(this / other.value, other.precision)

operator fun Decimal.unaryMinus() = Decimal(-value, precision)

val Decimal.sqrt get() = Decimal(sqrt(value), precision)

fun exp(value: Decimal) = Decimal(exp(value.value), value.precision)

fun Decimal.ceilToInt(): Int = ceil(value).toInt()

fun Decimal.roundToInt(): Int = value.roundToInt()

fun Decimal.abs() = Decimal(value.absoluteValue, precision)

val Decimal.absoluteValue get() = value.absoluteValue

fun Decimal.withPrecision(precision: Int) = Decimal(value, precision)

fun Decimal.withMaxPrecision(precision: Int) = Decimal(value, minOf(this.precision, precision))

fun Decimal.round(precision: Int) = Decimal(value.round(precision), precision)

fun String.parseDecimal(): Decimal? {
    val tailZeros = takeLastWhile { it == '0' }.length
    val precision = when (val decimalPointIndex = indexOf('.')) {
        -1 -> 0
        else -> length - decimalPointIndex - tailZeros - 1
    }
    return toDoubleOrNull()?.withPrecision(precision)
}

fun Double.toDecimal() = toString().parseDecimal()!!
fun Int.toDecimal() = Decimal(toDouble(), 0)

fun Double.snap(grid: Decimal): Decimal {
    if (grid == zero || grid.isNaN()) error("Invalid grid value: $grid")
    if (grid < zero) return snap(-grid)
    if (this < 0.0) return -(-this).snap(grid)
    var v = zero(grid.precision)
    val max = this + grid.value / 2
    for (i in 20 downTo 0) {
        val f = 2.0.pow(i)
        if ((v + f * grid).value <= max) v += f * grid
    }
    return v.withPrecision(grid.precision)
}

fun Decimal.wrapAt(divisor: Decimal): Decimal = when {
    this < zero -> -(-this).wrapAt(divisor)
    this < divisor -> this
    else -> this - value.snap(divisor)
}

fun timeCode(t: Decimal, accuracy: Int): String {
    var seconds = t.toInt()
    val milliseconds = ((t - seconds) * 10.0.pow(accuracy)).toInt()
    val minutes = seconds / 60
    seconds %= 60
    return when {
        accuracy == 0 && minutes == 0 -> "$seconds"
        accuracy == 0 -> String.format("%d:%02d", minutes, seconds)
        minutes == 0 -> String.format("%d,%0${accuracy}d", seconds, milliseconds)
        else -> String.format("%d:%02d,%0${accuracy}d", minutes, seconds, milliseconds)
    }
}

fun DoubleRange.reverseIfEmpty() = if (start > endInclusive) endInclusive..start else this

fun accuracy(delta: Double) = ceil(-log10(delta).coerceAtMost(0.0)).toInt()

fun Double.round(accuracy: Int): Double {
    val factor = 10.0.pow(accuracy)
    return (this * factor).roundToLong() / factor
}

val Decimal.sign get() = value.sign

fun Decimal.isMultipleOf(factor: Decimal): Boolean {
    val quotient = this / factor
    return quotient.isInteger
}

val Decimal.isInteger get() = '.' !in toCanonicalString()

fun Long.toSeconds() = (this / 1000.0).asTime

fun Decimal.tanh() = tanh(value).toDecimal()