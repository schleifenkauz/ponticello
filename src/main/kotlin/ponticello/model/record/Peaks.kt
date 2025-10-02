package ponticello.model.record

data class Peaks(
    val size: Int,
    private val minima: List<Float>, private val maxima: List<Float>
) {
    val indices = minima.indices

    fun getMin(idx: Int): Float = when (idx) {
        in indices -> minima[idx]
        in 0 until size -> 0.0f
        else -> throw IndexOutOfBoundsException("Invalid index $idx. Size = $size")
    }

    fun getMax(idx: Int) = when (idx) {
        in indices -> maxima[idx]
        in 0 until size -> 0.0f
        else -> throw IndexOutOfBoundsException("Invalid index $idx. Index = $idx")
    }
}