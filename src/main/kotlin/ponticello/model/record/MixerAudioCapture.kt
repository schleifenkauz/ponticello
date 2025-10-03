package ponticello.model.record

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.*
import kotlin.concurrent.thread

class MixerAudioCapture(
    private val format: AudioFormat,
    private val mixer: Mixer,
    private val bufferSize: Int,
) : AbstractAudioCapture() {
    private val buf = ByteArray(bufferSize * 2 * format.channels)
    private var activeThread: Thread? = null
    private var activeLine: TargetDataLine? = null
    private lateinit var floatBuffers: List<FloatArray>

    override fun doPrepare(): Boolean {
        floatBuffers = List(channelConfig.outputChannels) { FloatArray(bufferSize) }

        val info = DataLine.Info(TargetDataLine::class.java, format)
        val line = try {
            mixer.getLine(info) as TargetDataLine
        } catch (e: LineUnavailableException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
        activeLine = line
        line.open(format)

        val thread = thread {
            run()
        }
        activeThread = thread

        return true
    }

    override fun doStart(): Boolean {
        activeLine?.start()
        return true
    }

    override fun doStop() {
        activeLine?.flush()
        activeLine?.stop()
    }

    override fun doClose() {
        activeLine?.close()
        activeLine = null
        activeThread?.interrupt()
        activeThread = null
    }

    private fun run() {
        try {
            val line = activeLine
            while (line != null && !Thread.interrupted()) {
                if (line.available() == 0) Thread.sleep(10)
                val bytesRead = line.read(buf, 0, line.available().coerceAtMost(buf.size))
                if (bytesRead == 0) break
                val frames = bytesRead / (format.channels * 2)
//                require(bytesRead == bufferSize * format.channels * 2) { "Invalid buffer size: $bytesRead" }

                val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until frames) {
                    for (j in 0 until format.channels) {
                        val outputIdx = channelConfig.map(j)
                        floatBuffers[outputIdx][i] = bb.getShort().toFloat() / Short.MAX_VALUE
                    }
                }
                buffer.receive(floatBuffers, frames)
            }
        } catch (e: InterruptedException) {
            return
        }
    }
}