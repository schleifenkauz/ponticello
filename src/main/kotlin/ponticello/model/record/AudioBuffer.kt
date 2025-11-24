package ponticello.model.record

import ponticello.impl.Decimal
import ponticello.impl.DecimalRange

interface AudioBuffer {
    val sampleRate: Double
    val bufferSize: Int

    val currentPosition: Decimal

    fun read(range: DecimalRange): List<Float>

    fun append(samples: FloatArray, frames: Int)

    fun clear()

    fun totalSamples(): Long

    fun addListener(listener: Listener)

    interface Listener {
        fun accept(sampleOffset: Long, samples: FloatArray, frames: Int)

        fun onClear() {}

        fun afterCleared() {}
    }
}