package ponticello.model.record

import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.impl.Logger
import ponticello.sc.client.SuperColliderClient
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

@Serializable
sealed class CaptureSource {
    abstract val name: String

    abstract val channels: Int

    abstract fun capture(context: Context): AudioCapture?

    data object None : CaptureSource() {
        override val name: String
            get() = "<none>"

        override val channels: Int get() = 0

        override fun capture(context: Context): AudioCapture? = null
    }

    @Serializable
    data class Mixer(
        override val name: String,
        val bufferSize: Int,
        override val channels: Int,
    ) : CaptureSource() {
        override fun capture(context: Context): MixerAudioCapture? {
            val sampleRate = context[SuperColliderClient].sampleRate
            val format = getFormat(sampleRate, channels)
            val mixerInfo = AudioSystem.getMixerInfo().find { info -> info.name == name }
            if (mixerInfo == null) {
                Logger.error("Invalid mixer name: $name")
                return null
            }
            val mixer = AudioSystem.getMixer(mixerInfo)
            return MixerAudioCapture(format, mixer, bufferSize)
        }

        companion object {
            private const val BUFFER_SIZE = 1024
            private const val SAMPLE_SIZE = 16

            private fun getFormat(sampleRate: Double, channels: Int) =
                AudioFormat(sampleRate.toFloat(), SAMPLE_SIZE, channels, true, false)

            fun getAvailableSources(sampleRate: Double) = AudioSystem.getMixerInfo().filter { info ->
                val mixer = AudioSystem.getMixer(info) ?: return@filter false
                val channels = getChannels(mixer) ?: return@filter false
                val format = getFormat(sampleRate, channels)
                mixer.isLineSupported(DataLine.Info(TargetDataLine::class.java, format))
            }.map { info ->
                val channels = getChannels(AudioSystem.getMixer(info))!!
                Mixer(info.name, BUFFER_SIZE, channels)
            }

            private fun getChannels(mixer: javax.sound.sampled.Mixer) =
                mixer.targetLineInfo.filterIsInstance<DataLine.Info>()
                    .maxOfOrNull { lineInfo -> lineInfo.formats.maxOf { f -> f.channels } }

        }
    }

    @Serializable
    data class Jack(override val name: String) : CaptureSource() {
        override fun capture(context: Context): JackAudioCapture = JackAudioCapture(name)

        override val channels: Int
            get() = JackAudioCapture.getOutputChannels(name)

        companion object {
            fun getAvailableSources() = JackAudioCapture.getSources().map(::Jack)
        }
    }
}