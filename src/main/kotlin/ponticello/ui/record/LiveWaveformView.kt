package ponticello.ui.record

import javafx.application.Platform
import javafx.scene.paint.Color
import ponticello.impl.DecimalRange

class LiveWaveformView(
    private val peaks: WaveformPeaks,
    initialDisplayRange: DecimalRange
) : LiveAudioBufferView(initialDisplayRange), AudioBuffer.Listener {
    init {
        peaks.buffer.addListener(this)
    }

    override fun repaint() {
        val peaks = peaks.getPeaks(displayRange, width)
        Platform.runLater {
            with(graphicsContext) {
                fill = Color.BLACK
                fillRect(0.0, 0.0, width, height)
                stroke = Color.LIMEGREEN
                for (i in peaks.indices) {
                    val x = width * i / peaks.size
                    val h = height / 2
                    val yMin = h - (peaks.getMin(i) * h * 5)
                    val yMax = h - (peaks.getMax(i) * h * 5)
                    strokeLine(x, yMin, x, yMax)
                }
            }
        }
    }

    override fun accept(sampleOffset: Long, samples: DoubleArray) {
        repaint()
    }
}