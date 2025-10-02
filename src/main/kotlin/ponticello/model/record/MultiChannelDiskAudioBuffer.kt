package ponticello.model.record

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MultiChannelDiskAudioBuffer(
    private val file: File,
    sampleRate: Double, channelConfig: ChannelConfiguration, bufferSize: Int
) : MultiChannelAudioBuffer(sampleRate, channelConfig, bufferSize) {
    private val raf = RandomAccessFile(file, "rw")
    private val channel = raf.channel
    private val floatBuffers = List(channelConfig.inputChannels) { FloatArray(bufferSize) }
    override val channels: List<AudioBuffer> = List(channelConfig.outputChannels) { ch -> ChannelBuffer(this, ch) }

//    private var currentRangeBuffer: FloatArray = FloatArray(0)
//    private var currentRange: DecimalRange? = null
//
//    private fun read(range: DecimalRange) {
//        currentRange = range
//        currentRangeBuffer = FloatArray(bufferSize)
//    }

    override fun appendBytes(bytes: ByteArray) {
        require(bytes.size == bufferSize * 2) { "Invalid buffer size: ${bytes.size}" }
        channel.position(channel.size())
        val bb = ByteBuffer.wrap(bytes)
        while (bb.hasRemaining()) {
            channel.write(bb)
        }
        readInto(channels, bytes, floatBuffers)
    }

    private class ChannelBuffer(
        private val fileBuffer: MultiChannelDiskAudioBuffer,
        private val channel: Int
    ) : AbstractAudioBuffer(fileBuffer.sampleRate, fileBuffer.bufferSize) {
        val listeners = mutableListOf<AudioBuffer.Listener>()

        override fun read(offset: Long, len: Int): FloatArray {
            fileBuffer.raf.seek(offset * fileBuffer.channelConfig.inputChannels)
            val bytes = ByteArray(len * channel)
            fileBuffer.raf.read(bytes)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val dest = FloatArray(len)
            for (i in 0 until len) {
                for (ch in 0 until fileBuffer.channelConfig.inputChannels) {
                    if (fileBuffer.channelConfig.mapping[ch] == channel) {
                        dest[i] += bb.getShort().toFloat() / Short.MAX_VALUE
                    }
                }
            }
            return dest
        }

        override fun addListener(listener: AudioBuffer.Listener) {
            listeners.add(listener)
        }
    }
}