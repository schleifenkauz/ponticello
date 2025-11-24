package ponticello.ui.record

import javafx.scene.paint.Color
import ponticello.impl.DecimalRange
import ponticello.impl.zero
import ponticello.model.record.AudioBuffer

class SimpleLiveWaveformCanvas(
    private val buffer: AudioBuffer, initialDisplayRange: DecimalRange
) : LiveAudioBufferCanvas(initialDisplayRange) {
    override fun repaint() {
        if (width == 0.0 || height == 0.0) return
        clearCanvas()
        check(displayRange.start >= zero)
        val samples = buffer.read(displayRange)
        println("Buffer duration: ${samples.size / buffer.sampleRate}, ${buffer.currentPosition}")
        with(graphicsContext2D) {
            stroke = Color.LIMEGREEN
            for (x in 0 until width.toInt()) {
                val dt = x / pixelsPerSecond
                val i = (dt * buffer.sampleRate).toInt()
                if (i >= samples.size) break
                val h = height / 2
                val v = samples[i]
                strokeLine(x.toDouble(), h - v * h, x.toDouble(), h + v * h)
            }
        }
    }
}