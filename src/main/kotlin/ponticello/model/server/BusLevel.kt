package ponticello.model.server

data class BusLevel(val rms: List<Double>, val peak: List<Double>) {
    val channels = rms.indices

    init {
        require(rms.size == peak.size)
    }

    operator fun plus(value: Double) = BusLevel(rms.map { it + value }, peak.map { it + value })
}