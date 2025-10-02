package ponticello.model.record

import javax.sound.sampled.*

class MixerAudioCapture(
    private val format: AudioFormat,
    private val mixer: Mixer,
    private val bufferSize: Int
) : AbstractAudioCapture(format.channels) {
    private val buf = ByteArray(bufferSize * 2 * format.channels)
    private var activeThread: Thread? = null
    private var activeLine: TargetDataLine? = null

    override fun doPrepare(): Boolean {
        val info = DataLine.Info(TargetDataLine::class.java, format, bufferSize)
        val line = try {
            mixer.getLine(info) as TargetDataLine
        } catch (e: LineUnavailableException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
        activeLine = line
        line.open(format)

        val thread = Thread(::run, "LiveAudioCapture [${mixer.mixerInfo.name}]")
        thread.isDaemon = true
        thread.start()
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
            while (activeLine != null) {
                val bytesRead = activeLine!!.read(buf, 0, buf.size)
                if (bytesRead == buf.size) {
                    buffer.appendBytes(buf)
                }
            }
        } catch (e: InterruptedException) {
            return
        }
    }
}