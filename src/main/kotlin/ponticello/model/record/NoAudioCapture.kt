package ponticello.model.record

import ponticello.impl.Logger
import reaktive.value.ReactiveValue
import reaktive.value.reactiveValue

object NoAudioCapture : AudioCapture {
    override val status: ReactiveValue<AudioCapture.Status>
        get() = reactiveValue(AudioCapture.Status.INVALID)

    override fun prepare(dest: MultiChannelAudioBuffer, config: ChannelConfiguration, threshold: LoudnessThreshold): Boolean = false

    override fun start() {
        Logger.error("No audio capture device available.")
    }

    override fun stop() {
        Logger.error("No audio capture device available.")
    }

    override fun close() {
        Logger.error("No audio capture device available.")
    }
}