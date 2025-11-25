package ponticello.model.record

import ponticello.impl.*
import java.nio.FloatBuffer

abstract class AbstractAudioBuffer(final override val sampleRate: Double) : AudioBuffer {
    private val listeners = mutableListOf<AudioBuffer.Listener>()
    private var nSamples = 0L

    override val currentPosition: Decimal
        get() = (nSamples / sampleRate).withPrecision(4)

    override fun read(range: DecimalRange): List<Float> {
        val sampleOffset = (range.start * sampleRate).toLong()
        val availableSamples = (nSamples - sampleOffset).toInt()
        if (availableSamples <= 0) return emptyList()
        val samples = (range.duration * sampleRate).toInt().coerceAtMost(availableSamples)
        return read(sampleOffset, samples)
    }

    protected abstract fun read(offset: Long, len: Int): List<Float>

    override fun append(samples: FloatBuffer, frames: Int) {
        val sampleOffset = nSamples
        nSamples += frames
        for (listener in listeners) {
            listener.accept(sampleOffset, samples.position(0), frames)
        }
    }

    override fun clear() {
        nSamples = 0L
        for (listener in listeners) {
            listener.onClear()
        }
    }

    override fun totalSamples(): Long = nSamples

    override fun addListener(listener: AudioBuffer.Listener) {
        listeners.add(listener)
    }
}