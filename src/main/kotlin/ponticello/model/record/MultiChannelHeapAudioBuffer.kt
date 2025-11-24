package ponticello.model.record

import ponticello.impl.DecimalRange
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

class MultiChannelHeapAudioBuffer(
    nChannels: Int, sampleRate: Double, initialCapacity: Int,
) : MultiChannelAudioBuffer(sampleRate, nChannels) {
    override val channels: List<AudioBuffer> = List(nChannels) {
        HeapAudioBuffer(sampleRate, initialCapacity)
    }

    override fun writeTo(file: File, format: AudioFormat, range: DecimalRange) {
        val channelsArrays = channels.map { ch -> ch.read(range) }
        val frames = channelsArrays.map { arr -> arr.size }.toSet().single()
        val bytes = ByteArray(frames * nChannels * 2)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frames) {
            for (ch in 0 until nChannels) {
                val sample = channelsArrays[ch][frame]
                val asShort = (sample * Short.MAX_VALUE).toInt().toShort()
                buf.putShort(asShort)
            }
        }
        val audioStream = AudioInputStream(ByteArrayInputStream(bytes), format, bytes.size.toLong())
        AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, file)
    }
}