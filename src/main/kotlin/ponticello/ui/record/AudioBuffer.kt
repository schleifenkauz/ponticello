package ponticello.ui.record

import ponticello.impl.Decimal
import ponticello.impl.DecimalRange

interface AudioBuffer {
    val sampleRate: Double
    val bufferSize: Int

    val currentPosition: Decimal

    fun read(range: DecimalRange): DoubleArray

    fun append(bytes: ByteArray)

    fun totalSamples(): Long

    fun addListener(listener: Listener)

    interface Listener {
        fun accept(sampleOffset: Long, samples: DoubleArray)
    }
}