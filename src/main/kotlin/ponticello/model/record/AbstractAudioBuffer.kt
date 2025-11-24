package ponticello.model.record

import ponticello.impl.*

abstract class AbstractAudioBuffer(
    final override val sampleRate: Double,
    final override val bufferSize: Int
) : AudioBuffer {
    private val listeners = mutableListOf<AudioBuffer.Listener>()
    private var nSamples = 0L

    override val currentPosition: Decimal
        get() = (nSamples / sampleRate).withPrecision(4)

    override fun read(range: DecimalRange): List<Float> {
        val sampleOffset = (range.start * sampleRate).toLong()
        val availableSamples = (nSamples - sampleOffset).toInt()
        if (availableSamples <= 0) return emptyList()
        val samples = (range.dur * sampleRate).toInt().coerceAtMost(availableSamples)
        return read(sampleOffset, samples)
    }

    protected abstract fun read(offset: Long, len: Int): List<Float>

    override fun append(samples: FloatArray, frames: Int) {
//        require(frames == bufferSize) { "Invalid buffer size: ${samples.size}" }
        val sampleOffset = nSamples
        nSamples += samples.size
        for (listener in listeners) {
            listener.accept(sampleOffset, samples, frames)
        }
    }

    override fun clear() {
        nSamples = 0L
        for (listener in listeners) {
            listener.onClear()
        }
        for (listener in listeners) {
            listener.afterCleared()
        }
    }

    override fun totalSamples(): Long = nSamples

    override fun addListener(listener: AudioBuffer.Listener) {
        listeners.add(listener)
    }
}