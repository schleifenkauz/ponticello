package ponticello.ui.record

import java.nio.ByteBuffer
import java.nio.ByteOrder

class HeapAudioBuffer(
    sampleRate: Double, bufferSize: Int, initialCapacity: Int
) : AbstractAudioBuffer(sampleRate, bufferSize) {
    private var buffer = DoubleArray(initialCapacity)

    override fun read(samples: Int, offset: Long, len: Int): DoubleArray {
        val arr = DoubleArray(samples)
        System.arraycopy(buffer, offset.toInt(), arr, 0, len)
        return arr
    }

    override fun append(bytes: ByteArray) {
        val nSamples = samples()
        val minCapacity = bytes.size / 2 + nSamples
        if (minCapacity > buffer.size) {
            buffer = buffer.copyOf(newSize = minCapacity * 2)
        }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until bytes.size / 2) {
            val v = bb.getShort() / Short.MAX_VALUE.toDouble()
            buffer[nSamples + i] = v
        }
        super.append(bytes)
    }
}