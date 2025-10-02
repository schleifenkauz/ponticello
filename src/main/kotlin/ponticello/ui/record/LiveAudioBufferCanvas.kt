package ponticello.ui.record

import javafx.scene.canvas.Canvas
import ponticello.impl.DecimalRange
import ponticello.impl.Logger
import ponticello.impl.minus
import ponticello.impl.zero

abstract class LiveAudioBufferCanvas(initialDisplayRange: DecimalRange) : Canvas() {
    var displayRange: DecimalRange = initialDisplayRange
        private set

    abstract fun repaint()

    fun display(range: DecimalRange) {
        if (range.isEmpty()) {
            Logger.severe("Attempt to display empty time range: $range", Logger.Category.Score)
            return
        }
        displayRange =
            if (displayRange.start >= zero) range
            else range - range.start
        repaint()
    }
}