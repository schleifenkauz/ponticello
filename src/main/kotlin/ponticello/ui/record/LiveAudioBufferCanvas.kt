package ponticello.ui.record

import javafx.application.Platform
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color.BLACK
import ponticello.impl.DecimalRange
import ponticello.impl.Logger
import ponticello.impl.rangeTo
import ponticello.impl.zero
import ponticello.model.record.AudioBuffer

abstract class LiveAudioBufferCanvas(initialDisplayRange: DecimalRange) : Canvas(), AudioBuffer.Listener {
    var displayRange: DecimalRange = initialDisplayRange
        private set(value) {
            require(value.start >= zero)
            require(value.endInclusive > value.start)
            field = value
        }

    init {
        widthProperty().addListener { repaint() }
        heightProperty().addListener { repaint() }
    }

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
        Platform.runLater {
            displayRange =
                if (range.start >= zero) range
                else zero..(range.endInclusive + range.start)
            repaint()
        }
    }

    override fun afterCleared() {
        Platform.runLater {
            repaint()
        }
    }

    override fun accept(sampleOffset: Long, samples: FloatArray, frames: Int) {
        Platform.runLater {
            repaint()
        }
    }
}