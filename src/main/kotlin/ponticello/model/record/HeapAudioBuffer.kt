package ponticello.model.record

import java.nio.ByteBuffer
import java.nio.ByteOrder

class HeapAudioBuffer(
    sampleRate: Double, bufferSize: Int, initialCapacity: Int
) : AbstractAudioBuffer(sampleRate, bufferSize) {
    private var buffer = DoubleArray(initialCapacity)

    override fun read(offset: Long, len: Int): DoubleArray {
        return buffer.copyOfRange(offset.toInt(), offset.toInt() + len)
    }

    override fun append(bytes: ByteArray) {
        val nSamples = totalSamples().toInt()
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