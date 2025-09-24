package ponticello.ui.live

fun Int.pow2() = 1 shl this

typealias DoubleRange = ClosedFloatingPointRange<Double>

val DoubleRange.dur get() = endInclusive - start