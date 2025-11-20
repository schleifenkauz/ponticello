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
    abstract val bufferSize: Int

    abstract val channels: Int

    abstract fun capture(context: Context): AudioCapture?

    abstract fun getFormat(sampleRate: Double): AudioFormat?

    data object None : CaptureSource() {
        override val bufferSize: Int = 0
        override val channels: Int get() = 0

        override fun capture(context: Context): AudioCapture? = null

        override fun getFormat(sampleRate: Double): AudioFormat? = null
    }

    @Serializable
    data class Mixer(
        val name: String,
        override val bufferSize: Int,
        override val channels: Int,
    ) : CaptureSource() {
        override fun capture(context: Context): MixerAudioCapture? {
            val sampleRate = context[SuperColliderClient].sampleRate.toFloat()
            val format = getFormat(sampleRate.toDouble(), channels)
            val mixerInfo = AudioSystem.getMixerInfo().find { info -> info.name == name }
            if (mixerInfo == null) {
                Logger.error("Invalid mixer name: $name")
                return null
            }
            val mixer = AudioSystem.getMixer(mixerInfo)
            return MixerAudioCapture(format, mixer, bufferSize)
        }

        override fun getFormat(sampleRate: Double): AudioFormat = getFormat(sampleRate, channels)

        companion object {
            private const val BUFFER_SIZE = 1024
            private const val SAMPLE_SIZE = 16

            private fun getFormat(sampleRate: Double, channels: Int) =
                AudioFormat(sampleRate.toFloat(), SAMPLE_SIZE, channels, true, false)

            fun getAvailable(sampleRate: Double) = AudioSystem.getMixerInfo().filter { info ->
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
}