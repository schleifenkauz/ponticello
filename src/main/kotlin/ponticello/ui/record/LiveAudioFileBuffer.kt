package ponticello.ui.record

import ponticello.impl.DecimalRange
import ponticello.impl.dur
import ponticello.impl.times
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LiveAudioFileBuffer(file: File, val sampleRate: Double, val bufferSize: Int) {
    private val raf = RandomAccessFile(file, "rw")
    private val channel = raf.channel

    private val listeners = mutableListOf<Listener>()
    private var nSamples = 0

    fun read(range: DecimalRange): DoubleArray {
        val samples = (range.dur * sampleRate * 2).toInt()
        val sampleOffset = (range.start * sampleRate).toLong()
        raf.seek(sampleOffset)
        val bytes = ByteArray(samples) //TODO reuse
        raf.read(bytes)
        return bytes.toDoubleArray()
    }

    fun append(bytes: ByteArray) {
        require(bytes.size == bufferSize * 2) { "Invalid buffer size: ${bytes.size}" }
        channel.position(channel.size())
        var bb = ByteBuffer.wrap(bytes)
        while (bb.hasRemaining()) {
            channel.write(bb)
        }
        val samples = bytes.size / 2
        val arr = bytes.toDoubleArray()
        for (listener in listeners) {
            listener.accept(arr)
        }
        nSamples += samples
    }

    fun samples() = nSamples

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    interface Listener {
        fun accept(samples: DoubleArray)
    }

    companion object {
        private fun ByteArray.toDoubleArray(): DoubleArray {
            val samples = size / 2
            val bb = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
            return DoubleArray(samples) {
                bb.getShort() / Short.MAX_VALUE.toDouble()
            }
        }
    }
}