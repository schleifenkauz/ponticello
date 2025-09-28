package ponticello.ui.record

data class Peaks(
    val size: Int,
    private val minima: List<Double>, private val maxima: List<Double>
) {
    val indices = minima.indices

    fun getMin(idx: Int): Double = when (idx) {
        in minima.indices -> minima[idx]
        in 0 until size -> 0.0
        else -> throw IndexOutOfBoundsException("Invalid index $idx. Size = $size")
    }

    fun getMax(idx: Int) = when (idx) {
        in minima.indices -> minima[idx]
        in 0 until size -> 0.0
        else -> throw IndexOutOfBoundsException("Invalid index $idx. Index = $idx")
    }
}