package ponticello.model.record

import hextant.context.Context
import ponticello.impl.DecimalRange
import ponticello.impl.duration
import ponticello.impl.times
import ponticello.model.instr.BusObject
import ponticello.sc.client.ScWriter
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.sound.sampled.AudioFormat

class MultiChannelDiskAudioBuffer(
    private val file: File, sampleRate: Double, nChannels: Int
) : MultiChannelAudioBuffer(sampleRate, nChannels) {
    private val raf = RandomAccessFile(file, "rw")
    private val channel = raf.channel
    override val channels: List<AudioBuffer> = List(nChannels) { ch -> ChannelBuffer(this, ch) }

    override fun write(samples: List<FloatBuffer>, frames: Int) {
        val buf = ByteBuffer.allocate(frames * nChannels * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            for (ch in 0 until nChannels) {
                val v = samples[ch][i]
                val asShort = (v * Short.MAX_VALUE).toInt().toShort()
                buf.putShort(asShort)
            }
        }
        channel.write(buf)
    }

    override fun writeTo(file: File, format: AudioFormat, range: DecimalRange) {
        val position = (range.start * sampleRate * 2).toLong()
        val count = (range.duration * sampleRate * 2).toLong()
        val output = file.outputStream().channel
        raf.channel.transferTo(position, count, output)
    }

    override fun loadBuffer(
        range: DecimalRange, format: AudioFormat, context: Context,
        action: ScWriter.(bufName: String) -> Unit
    ) {
        val frameOffset = (range.start * sampleRate).toLong()
        val numFrames = (range.duration * sampleRate).toLong()
        loadBuffer(file, frameOffset, numFrames, context, action)
    }

    override fun playBuffer(range: DecimalRange, outBus: BusObject, format: AudioFormat, context: Context) {

    }

    private class ChannelBuffer(
        private val fileBuffer: MultiChannelDiskAudioBuffer,
        private val channel: Int
    ) : AbstractAudioBuffer(fileBuffer.sampleRate) {
        override fun read(offset: Long, len: Int): List<Float> {
            fileBuffer.raf.seek(offset * fileBuffer.nChannels)
            val bytes = ByteArray(len * channel)
            fileBuffer.raf.read(bytes)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val dest = FloatArray(len)
            for (i in 0 until len) {
                for (ch in 0 until fileBuffer.nChannels) {
                    if (ch == channel) {
                        dest[i] = bb.getShort().toFloat() / Short.MAX_VALUE
                    }
                }
            }
            return dest.asList()
        }
    }
}