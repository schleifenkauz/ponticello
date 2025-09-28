package ponticello.ui.record

import javafx.scene.canvas.Canvas
import javafx.scene.layout.StackPane
import ponticello.impl.DecimalRange

class SpectrogramCanvas(displayRange: DecimalRange) : StackPane() {
    private val canvas = Canvas()

    var displayRange: DecimalRange = displayRange
}