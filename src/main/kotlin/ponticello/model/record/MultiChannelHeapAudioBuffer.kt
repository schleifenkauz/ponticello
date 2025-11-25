package ponticello.model.record

import hextant.context.Context
import ponticello.impl.DecimalRange
import ponticello.impl.times
import ponticello.model.instr.BusObject
import ponticello.model.obj.project
import ponticello.sc.client.ScWriter
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

    private fun createTempFile(context: Context): File {
        val tmpDir = context.project.projectDirectory.resolve("tmp")
        tmpDir.mkdirs()
        val idx = tmpFileCounter++
        val tmpFile = tmpDir.resolve("tmp$idx.wav")
        return tmpFile
    }

    override fun loadBuffer(
        range: DecimalRange, format: AudioFormat, context: Context,
        action: ScWriter.(bufName: String) -> Unit
    ) {
        val tmpFile = createTempFile(context)
        writeTo(tmpFile, format, range)
        loadBuffer(tmpFile, frameOffset = 0, numFrames = -1, context, action)
    }

    override fun playBuffer(range: DecimalRange, outBus: BusObject, format: AudioFormat, context: Context) {
        val tmpFile = createTempFile(context)
        writeTo(tmpFile, format, range)
        playBuffer(tmpFile, 0, outBus, context)
    }

    companion object {
        private var tmpFileCounter = 0
    }
}