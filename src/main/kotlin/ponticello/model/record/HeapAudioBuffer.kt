package ponticello.model.record

import java.nio.FloatBuffer
import java.util.*

class HeapAudioBuffer(sampleRate: Double, initialCapacity: Int) : AbstractAudioBuffer(sampleRate) {
    private var buffer = FloatArray(initialCapacity)

    override fun read(offset: Long, len: Int): List<Float> {
        return buffer.asList().subList(offset.toInt(), offset.toInt() + len)
    }

    override fun append(samples: FloatBuffer, frames: Int) {
        ensureCapacity(additionalSize = samples.remaining())
        val offset = totalSamples().toInt()
        samples.position(0).get(buffer, offset, frames)
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