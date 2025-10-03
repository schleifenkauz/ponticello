package ponticello.model.record

class HeapAudioBuffer(
    sampleRate: Double, bufferSize: Int, initialCapacity: Int
) : AbstractAudioBuffer(sampleRate, bufferSize) {
    private var buffer = FloatArray(initialCapacity)

    override fun read(offset: Long, len: Int): FloatArray {
        return buffer.copyOfRange(offset.toInt(), offset.toInt() + len)
    }

    override fun append(samples: FloatArray, frames: Int) {
        ensureCapacity(additionalSize = samples.size)
        val offset = totalSamples().toInt()
        System.arraycopy(samples, 0, buffer, offset, samples.size)
        super.append(samples, frames)
    }

    private fun ensureCapacity(additionalSize: Int) {
        val nSamples = totalSamples().toInt()
        val minCapacity = additionalSize + nSamples
        if (minCapacity > buffer.size) {
            buffer = buffer.copyOf(newSize = minCapacity * 2)
        }
    }
}