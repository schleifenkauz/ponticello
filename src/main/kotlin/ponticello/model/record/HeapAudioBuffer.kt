package ponticello.model.record

import java.util.*

class HeapAudioBuffer(
    sampleRate: Double, bufferSize: Int, initialCapacity: Int
) : AbstractAudioBuffer(sampleRate, bufferSize) {
    private var buffer = FloatArray(initialCapacity)

    override fun read(offset: Long, len: Int): List<Float> {
        return buffer.asList().subList(offset.toInt(), offset.toInt() + len)
    }

    override fun append(samples: FloatArray, frames: Int) {
        ensureCapacity(additionalSize = samples.size)
        val offset = totalSamples().toInt()
        System.arraycopy(samples, 0, buffer, offset, samples.size)
        super.append(samples, frames)
    }

    override fun clear() {
        super.clear()
        Arrays.fill(buffer, 0f)
    }

    private fun ensureCapacity(additionalSize: Int) {
        val nSamples = totalSamples().toInt()
        val minCapacity = additionalSize + nSamples
        if (minCapacity > buffer.size) {
            buffer = buffer.copyOf(newSize = minCapacity * 2)
        }
    }
}