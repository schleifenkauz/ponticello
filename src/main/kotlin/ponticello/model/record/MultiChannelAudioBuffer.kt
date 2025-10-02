package ponticello.model.record

import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class MultiChannelAudioBuffer(
    val sampleRate: Double,
    val channelConfig: ChannelConfiguration,
    val bufferSize: Int
) {
    abstract fun appendBytes(bytes: ByteArray)

    abstract val channels: List<AudioBuffer>

    fun getChannel(channel: Int): AudioBuffer = channels[channel]

    protected fun readInto(dest: List<AudioBuffer>, bytes: ByteArray, floatBuffers: List<FloatArray>) {
        require(bytes.size == bufferSize * dest.size * 2) { "Invalid buffer size: ${bytes.size}" }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until bufferSize) {
            for (ch in 0 until channelConfig.inputChannels) {
                floatBuffers[ch][i] = bb.getShort().toFloat() / Short.MAX_VALUE
            }
        }
        for (ch in 0 until channelConfig.outputChannels) {
            val destChannel = channelConfig.mapping[ch]
            dest[ch].append(floatBuffers[destChannel])
        }
    }
}