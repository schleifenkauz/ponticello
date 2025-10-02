package ponticello.model.record

class MultiChannelHeapAudioBuffer(
    sampleRate: Double, channelConfig: ChannelConfiguration, bufferSize: Int,
    initialCapacity: Int,
) : MultiChannelAudioBuffer(sampleRate, channelConfig, bufferSize) {
    private val floatBuffers = List(channelConfig.inputChannels) { FloatArray(bufferSize) }
    override val channels: List<AudioBuffer> = List(channelConfig.outputChannels) {
        HeapAudioBuffer(sampleRate, bufferSize, initialCapacity)
    }

    override fun appendBytes(bytes: ByteArray) {
        readInto(channels, bytes, floatBuffers)
    }
}