package ponticello.model.record

import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.impl.Logger
import ponticello.sc.client.SuperColliderClient
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

@Serializable
sealed class CaptureSource {
    abstract val bufferSize: Int

    abstract fun capture(context: Context): LiveAudioCapture?

    data object None : CaptureSource() {
        override val bufferSize: Int = 0
        override fun capture(context: Context): LiveAudioCapture? = null
    }

    @Serializable
    data class Mixer(
        val name: String,
        override val bufferSize: Int,
        val channels: Int,
    ) : CaptureSource() {
        override fun capture(context: Context): LiveAudioCapture? {
            val sampleRate = context[SuperColliderClient].sampleRate.toFloat()
            val format = AudioFormat(sampleRate, 16, channels, true, false)
            val mixerInfo = AudioSystem.getMixerInfo().find { info -> info.name == name }
            if (mixerInfo == null) {
                Logger.error("Invalid mixer name: $name")
                return null
            }
            val mixer = AudioSystem.getMixer(mixerInfo)
            return LiveAudioCapture(format, mixer, bufferSize)
        }
    }
}