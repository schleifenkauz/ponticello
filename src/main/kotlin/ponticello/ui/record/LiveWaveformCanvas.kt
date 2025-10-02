package ponticello.ui.record

import javafx.application.Platform
import javafx.scene.paint.Color
import ponticello.impl.DecimalRange
import ponticello.model.record.AudioBuffer
import ponticello.model.record.WaveformPeaks
import reaktive.Observer
import reaktive.value.now

class LiveWaveformCanvas(
    private val peaks: WaveformPeaks,
    initialDisplayRange: DecimalRange,
    private val config: LiveBufferViewConfig.Waveform,
) : LiveAudioBufferCanvas(initialDisplayRange), AudioBuffer.Listener {
    private val configObserver: Observer

    init {
        peaks.buffer.addListener(this)
        configObserver = config.yScale.observe { _, _, _ -> repaint() }
    }

    override fun repaint() {
        if (width == 0.0 || height == 0.0) return
        val peaks = peaks.getPeaks(displayRange, width)
        Platform.runLater {
            with(graphicsContext2D) {
                fill = Color.BLACK
                fillRect(0.0, 0.0, width, height)
                stroke = Color.LIMEGREEN
                for (i in peaks.indices) {
                    val x = width * i / peaks.size
                    val h = height / 2
                    val yScale = config.yScale.now
                    val yMin = h - (peaks.getMin(i) * h * yScale)
                    val yMax = h - (peaks.getMax(i) * h * yScale)
                    strokeLine(x, yMin, x, yMax)
                }
            }
        }
    }

    override fun accept(sampleOffset: Long, samples: FloatArray) {
        repaint()
    }
}