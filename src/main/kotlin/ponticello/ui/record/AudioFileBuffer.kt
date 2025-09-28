package ponticello.ui.record

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class AudioFileBuffer(file: File, sampleRate: Double, bufferSize: Int) : AbstractAudioBuffer(sampleRate, bufferSize) {
    private val raf = RandomAccessFile(file, "rw")
    private val channel = raf.channel

    override fun read(samples: Int, offset: Long, len: Int): DoubleArray {
        raf.seek(offset)
        val bytes = ByteArray(samples * 2) //TODO reuse
        raf.read(bytes, 0, len * 2)
        return bytes.toDoubleArray()
    }

    override fun append(bytes: ByteArray) {
        require(bytes.size == bufferSize * 2) { "Invalid buffer size: ${bytes.size}" }
        channel.position(channel.size())
        val bb = ByteBuffer.wrap(bytes)
        while (bb.hasRemaining()) {
            channel.write(bb)
        }
        super.append(bytes)
    }
}