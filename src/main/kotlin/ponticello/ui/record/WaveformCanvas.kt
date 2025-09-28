package ponticello.ui.record

import javafx.application.Platform
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color
import ponticello.impl.DecimalRange

class WaveformCanvas(
    private val peaks: WaveformPeaks,
    initialDisplayRange: DecimalRange
) : Canvas(), AudioBuffer.Listener {
    var displayRange: DecimalRange = initialDisplayRange
        set(value) {
            field = value
            repaint()
        }

    init {
        peaks.buffer.addListener(this)
    }

    fun repaint() {
        val peaks = peaks.getPeaks(displayRange, width)
        Platform.runLater {
            with(graphicsContext2D) {
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