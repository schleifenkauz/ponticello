package xenakis.impl

import java.util.*
import kotlin.math.*

typealias DoubleRange = ClosedFloatingPointRange<Double>

infix fun DoubleRange.step(step: Double) = sequence {
    var value = start
    while (value <= endInclusive) {
        yield(value)
        value += step
    }
}

fun Double.withPrecision(precision: Int) = Decimal(this, precision)

val zero = Decimal(0.0, 0)

val one = Decimal(1.0, 0)

val Double.dec get() = withPrecision(0)

operator fun Decimal.plus(other: Double) = Decimal(value + other, precision)

operator fun Decimal.minus(other: Double) = Decimal(value - other, precision)

operator fun Decimal.times(other: Double) = Decimal(value * other, precision)

operator fun Decimal.div(other: Double) = Decimal(value / other, precision)

operator fun Double.plus(other: Decimal) = Decimal(this + other.value, other.precision)

operator fun Double.minus(other: Decimal) = Decimal(this - other.value, other.precision)

operator fun Double.times(other: Decimal) = Decimal(this * other.value, other.precision)

operator fun Double.div(other: Decimal) = Decimal(this / other.value, other.precision)

fun Double.snap(grid: Double): Decimal {
    if (grid == 0.0 || grid.isNaN()) error("Invalid grid value: $grid")
    if (grid < 0.0) return snap(-grid)
    var v = 0.0
    for (i in 20 downTo 0) {
        val f = 2.0.pow(i)
        if (v + f * grid <= this.absoluteValue) v += f * grid
    }
    return Decimal(v * this.sign, accuracy(grid))
}

fun Double.wrapAt(divisor: Double): Decimal = this - snap(divisor)

fun timeCode(t: Double, accuracy: Int): String {
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

fun Double.format(accuracy: Int) = String.format(Locale.US, "%.${accuracy}f", this).dropLastWhile { c -> c == '0' }

fun Double.round(accuracy: Int): Double {
    val factor = 10.0.pow(accuracy)
    return (this * factor).roundToInt() / factor
}