package ponticello.ui.record

import javafx.application.Platform
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color.BLACK
import ponticello.impl.*
import ponticello.model.record.AudioBuffer
import java.nio.FloatBuffer

abstract class LiveAudioBufferCanvas(initialDisplayRange: DecimalRange) : Canvas(), AudioBuffer.Listener {
    var displayRange: DecimalRange = initialDisplayRange
        private set(value) {
            require(value.start >= zero)
            require(value.endInclusive > value.start)
            field = value
        }

    protected val pixelsPerSecond get() = width / displayRange.duration.toDouble()

    abstract fun repaint()

    protected fun clearCanvas() {
        graphicsContext2D.fill = BLACK
        graphicsContext2D.fillRect(0.0, 0.0, width, height)
    }

    fun display(range: DecimalRange) {
        if (range.isEmpty()) {
            Logger.severe("Attempt to display empty time range: $range", Logger.Category.Score)
            return
        }
        displayRange =
            if (range.start >= zero) range
            else zero..(range.endInclusive + range.start)
        repaint()
    }

    override fun accept(sampleOffset: Long, samples: FloatBuffer, frames: Int) {
        Platform.runLater {
            repaint()
        }
    }

    open fun clear() {
        Platform.runLater {
            repaint()
        }
    }
}