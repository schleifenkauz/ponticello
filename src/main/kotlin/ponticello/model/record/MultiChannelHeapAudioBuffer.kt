package ponticello.model.record

class MultiChannelHeapAudioBuffer(
    nChannels: Int, sampleRate: Double, bufferSize: Int,
    initialCapacity: Int,
) : MultiChannelAudioBuffer(sampleRate, nChannels, bufferSize) {
    override val channels: List<AudioBuffer> = List(nChannels) {
        HeapAudioBuffer(sampleRate, bufferSize, initialCapacity)
    }

    override fun receive(samples: List<FloatArray>, frames: Int) {
        for ((ch, arr) in samples.withIndex()) {
            channels[ch].append(arr, frames)
        }
    }
}