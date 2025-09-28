package ponticello.ui.record

import ponticello.impl.Decimal
import ponticello.impl.DecimalRange

interface AudioBuffer {
    val sampleRate: Double
    val bufferSize: Int

    fun read(range: DecimalRange): DoubleArray

    fun append(bytes: ByteArray)

    fun samples(): Int

    fun addListener(listener: Listener)

    interface Listener {
        fun accept(currentTime: Decimal, samples: DoubleArray)
    }
}