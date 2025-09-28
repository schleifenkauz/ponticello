package ponticello.ui.record

import ponticello.impl.DecimalRange
import ponticello.impl.dur
import ponticello.impl.times
import ponticello.impl.withPrecision
import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class AbstractAudioBuffer(
    final override val sampleRate: Double,
    final override val bufferSize: Int
) : AudioBuffer {
    private val listeners = mutableListOf<AudioBuffer.Listener>()
    private var nSamples = 0

    override fun read(range: DecimalRange): DoubleArray {
        val samples = (range.dur * sampleRate).toInt()
        val sampleOffset = (range.start * sampleRate).toLong()
        val availableSamples = (nSamples - sampleOffset).toInt().coerceAtLeast(0)
        val len = samples.coerceAtMost(availableSamples)
        return read(samples, sampleOffset, len)
    }

    protected abstract fun read(samples: Int, offset: Long, len: Int): DoubleArray

    override fun append(bytes: ByteArray) {
        val samples = bytes.size / 2
        nSamples += samples
        val arr = bytes.toDoubleArray()
        val currentTime = (nSamples / sampleRate).withPrecision(3)
        for (listener in listeners) {
            listener.accept(currentTime, arr)
        }
    }

    override fun samples() = nSamples

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