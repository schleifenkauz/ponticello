package ponticello.model.record

abstract class MultiChannelAudioBuffer(
    val sampleRate: Double,
    val nChannels: Int,
    val bufferSize: Int
) {
    abstract fun receive(samples: List<FloatArray>, frames: Int)

    abstract val channels: List<AudioBuffer>

    fun getChannel(channel: Int): AudioBuffer = channels[channel]
}