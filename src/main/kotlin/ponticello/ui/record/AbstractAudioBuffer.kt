package ponticello.ui.record

import ponticello.impl.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class AbstractAudioBuffer(
    final override val sampleRate: Double,
    final override val bufferSize: Int
) : AudioBuffer {
    private val listeners = mutableListOf<AudioBuffer.Listener>()
    private var nSamples = 0L

    override val currentPosition: Decimal
        get() = (nSamples / sampleRate).withPrecision(4)

    override fun read(range: DecimalRange): DoubleArray {
        val sampleOffset = (range.start * sampleRate).toLong()
        val availableSamples = (nSamples - sampleOffset).toInt().coerceAtLeast(0)
        val samples = (range.dur * sampleRate).toInt().coerceAtMost(availableSamples)
        return read(sampleOffset, samples)
    }

    protected abstract fun read(offset: Long, len: Int): DoubleArray

    override fun append(bytes: ByteArray) {
        val samples = bytes.size / 2
        val sampleOffset = nSamples
        nSamples += samples
        val arr = bytes.toDoubleArray()
        for (listener in listeners) {
            listener.accept(sampleOffset, arr)
        }
    }

    override fun totalSamples(): Long = nSamples

    override fun addListener(listener: AudioBuffer.Listener) {
        listeners.add(listener)
    }

    companion object {
        @JvmStatic
        protected fun ByteArray.toDoubleArray(): DoubleArray {
            val samples = size / 2
            val bb = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
            return DoubleArray(samples) {
                bb.getShort() / Short.MAX_VALUE.toDouble()
            }
        }
    }
}