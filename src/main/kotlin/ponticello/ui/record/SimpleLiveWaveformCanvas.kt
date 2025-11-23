package ponticello.ui.record

import javafx.scene.paint.Color
import ponticello.impl.DecimalRange
import ponticello.model.record.AudioBuffer

class SimpleLiveWaveformCanvas(
    private val buffer: AudioBuffer, initialDisplayRange: DecimalRange
) : LiveAudioBufferCanvas(initialDisplayRange) {
    override fun repaint() {
        if (width == 0.0 || height == 0.0) return
        clearCanvas()
        val samples = buffer.read(displayRange)
        with(graphicsContext2D) {
            stroke = Color.LIMEGREEN
            for (i in samples.indices) {
                val x = (width * i) / samples.size
                val h = height / 2
                val v = samples[i]
                strokeLine(x, v * h, x, v * -h)
            }
        }
    }
}