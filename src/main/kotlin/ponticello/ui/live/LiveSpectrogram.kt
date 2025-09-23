package ponticello.ui.live

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

class LiveSpectrogram : JPanel() {
    private val spectrogram = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
    private var drawX = 0
    private val running = true

    init {
        preferredSize = Dimension(WIDTH, HEIGHT)
        AudioSystem.getMixerInfo().forEach { info ->
            println(info)
        }
        Thread { this.startAudioCapture() }.start()
    }

    private fun startAudioCapture() {
        val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        try {
            AudioSystem.getTargetDataLine(format).use { line ->
                line.open(format, FFT_SIZE * 2)
                line.start()

                val buffer = ByteArray(FFT_SIZE * 2)
                while (running) {
                    val bytesRead = line.read(buffer, 0, buffer.size)
                    if (bytesRead == buffer.size) {
                        val samples = DoubleArray(FFT_SIZE)
                        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0..<FFT_SIZE) {
                            samples[i] = bb.getShort() / Short.MAX_VALUE.toDouble()
                        }

                        applyHannWindow(samples)
                        val spectrum = computeFFT(samples)
                        drawColumn(spectrum)
                        SwingUtilities.invokeLater {
                            repaint()
                        }
                    }
                }
            }
        } catch (e: LineUnavailableException) {
            e.printStackTrace()
        }
    }

    private fun drawColumn(magnitudes: DoubleArray) {
        for (y in magnitudes.indices) {
            val mag = magnitudes[y]
            val dB = 20 * log10(mag + 1e-6) // Avoid log(0)
            val norm = max(0.0, min(1.0, (dB + 60) / 60.0)).toFloat()
            val color = Color.getHSBColor(0.66f - norm * 0.66f, 1.0f, norm)
            spectrogram.setRGB(drawX, HEIGHT - 1 - y, color.rgb)
        }
        drawX = (drawX + 1) % WIDTH
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val left: Int = (drawX + 1) % WIDTH
        g.drawImage(spectrogram, 0, 0, WIDTH - left, HEIGHT, left, 0, WIDTH, HEIGHT, null)
        g.drawImage(spectrogram, WIDTH - left, 0, WIDTH, HEIGHT, 0, 0, left, HEIGHT, null)
    }

    companion object {
        private const val FFT_SIZE = 512
        private const val HEIGHT: Int = FFT_SIZE / 2
        private const val WIDTH = 800
        private const val SAMPLE_RATE = 44100

        @JvmStatic
        fun main(args: Array<String>) {
            SwingUtilities.invokeLater {
                val frame = JFrame("Live Spectrogram (Swing)")
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
                frame.contentPane = LiveSpectrogram()
                frame.pack()
                frame.isVisible = true
            }
        }
    }
}

