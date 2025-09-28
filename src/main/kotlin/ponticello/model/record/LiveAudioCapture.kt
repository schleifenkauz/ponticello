package ponticello.model.record

import ponticello.impl.Logger
import javax.sound.sampled.*

class LiveAudioCapture(
    private val format: AudioFormat,
    private val mixer: Mixer,
    private val bufferSize: Int
) {
    private val bytes = ByteArray(bufferSize * 2 * format.channels)
    private var activeThread: Thread? = null
    private var activeLine: TargetDataLine? = null

    private lateinit var buffer: AudioBuffer

    fun start(buffer: AudioBuffer) {
        if (activeThread != null) {
            Logger.error("Attempt to start LiveAudioCapture for input ${mixer.mixerInfo.name} while it is already running.")
            return
        }
        this.buffer = buffer
        val thread = Thread(::run, "LiveAudioCapture [${mixer.mixerInfo.name}]")
        thread.isDaemon = true
        thread.start()
        activeThread = thread
    }

    fun stop() {
        activeThread?.interrupt()
        activeThread = null
        activeLine?.stop()
    }

    private fun run() {
        try {
            val info = DataLine.Info(TargetDataLine::class.java, format, bufferSize)
            val line = mixer.getLine(info) as TargetDataLine
            activeLine = line
            line.open(format)
            line.start()
            while (!Thread.interrupted()) {
                val bytesRead = line.read(bytes, 0, bytes.size)
                if (bytesRead == bufferSize * 2) {
                    buffer.append(bytes)
                }
            }
        } catch (e: LineUnavailableException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            return
        }
    }
}