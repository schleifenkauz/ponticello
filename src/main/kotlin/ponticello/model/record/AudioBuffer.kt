package ponticello.model.record

import ponticello.impl.Decimal
import ponticello.impl.DecimalRange
import java.nio.FloatBuffer

interface AudioBuffer {
    val sampleRate: Double

    val currentPosition: Decimal

    fun read(range: DecimalRange): List<Float>

    fun append(samples: FloatBuffer, frames: Int)

    fun clear()

    fun totalSamples(): Long

    fun addListener(listener: Listener)

    interface Listener {
        fun accept(sampleOffset: Long, samples: FloatBuffer, frames: Int)

        fun onClear() {}
    }
}